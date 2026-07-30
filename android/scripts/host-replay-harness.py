#!/usr/bin/env python3
"""Record a match and replay it, speaking the Android frontend's IPC protocol.

Mirrors GameConnection.kt (control frames answered, everything else passed to
the recorder) and DemoRecorder.kt (frontend bytes verbatim, engine frames
re-prefixed with their length, control kinds dropped) so that a demo produced
here is byte-identical to one produced by the app. The point is the pair of
engine logs it leaves behind: record and replay must agree tick for tick.
"""
import argparse
import os
import shutil
import socket
import subprocess
import sys
import threading
import time

# DemoRecorder.CONTROL_KINDS — engine frames the frontend consumes itself.
CONTROL_KINDS = set("?CEiQqmHsbVvW~")

# uIO.pas isSyncedCommand: the frames that make up the match itself.
SYNCED = set("+#LlRrUuDdZzAaSjJ,cNpPwt12345fg")


def frame(msg):
    payload = msg.encode() if isinstance(msg, str) else msg
    assert len(payload) <= 255, f"frame too long: {len(payload)}"
    return bytes([len(payload)]) + payload


def frame_all(msgs):
    return b"".join(frame(m) for m in msgs)


def md5hex(s):
    import hashlib
    return hashlib.md5(s.encode()).hexdigest()


def config_commands(seed, turntime_ms, health, hogs, difficulty):
    """ConfigSerializer.localGame with an all-bot roster (bots play alone)."""
    cmds = [
        "TL",
        "etheme Nature",
        f"eseed {seed}",
        "e$gmflags 0",
        "e$damagepct 100",
        f"e$turntime {turntime_ms}",
        f"e$inithealth {health}",
        "e$sd_turns 15",
        "e$casefreq 5",
        "e$minestime 3000",
        "e$minesnum 4",
        "e$minedudpct 0",
        "e$explosives 2",
        "e$airmines 0",
        "e$sentries 0",
        "e$healthprob 35",
        "e$hcaseamount 25",
        "e$waterrise 47",
        "e$healthdec 5",
        "e$ropepct 100",
        "e$getawaytime 100",
        "e$worldedge 0",
        "e$template_filter 0",
        "e$feature_size 50",
        "e$mapgen 0",
        "e$scriptparam ",
    ]
    owner = md5hex("Player")
    for idx, (name, color) in enumerate((("Bravo", 4294967040), ("Charlie", 4278222848))):
        cmds += [
            # WeaponSet.DEFAULT — the engine rejects anything but 60 entries.
            "eammloadt 939192942219912103223511100120000000021110010101111100010001",
            "eammprob 040504054160065554655446477657666666615551010111541111111073",
            "eammdelay 000000000000020550000004000700400000000022000000060002000000",
            "eammreinf 131111031211111112311411111111111111121111111111111111111111",
            "eammstore",
            f"eaddteam {owner} {color} {name}",
            "egrave Statue",
            "efort Island",
            "evoicepack Default_qau",
            "eflag hedgewars",
        ]
        for h in range(hogs):
            cmds += [f"eaddhh {difficulty} {health} {name}{h + 1}", "ehat NoHat"]
    return cmds


class Session:
    """One engine run: owns the socket, records, and reports what it saw."""

    def __init__(self, engine, data, user_prefix, raw_config=None, config=None,
                 record=False, extra_args=(), timeout=600, stop_after_turns=0):
        self.engine, self.data, self.user_prefix = engine, data, user_prefix
        self.raw_config, self.config = raw_config, config
        self.record = record
        self.extra_args = list(extra_args)
        self.timeout = timeout
        # Diagnosing turn 1 does not need a whole match: cut the recording
        # short after N end-of-turn frames and replay the stump.
        self.stop_after_turns = stop_after_turns
        self.turns_seen = 0
        self.stopped_early = False
        self.demo = bytearray()
        self.valid = True
        self.stats = []
        self.finished = self.interrupted = False
        self.engine_synced_frames = []   # what the engine emitted, by kind
        self.error = None

    def _append(self, b):
        if self.valid:
            self.demo += b

    def _on_frontend(self, b):
        if self.record:
            self._append(b)

    def _on_engine_frame(self, payload):
        kind = chr(payload[0])
        if self.record and kind not in CONTROL_KINDS:
            self._append(bytes([len(payload)]) + payload)
        if kind in SYNCED or 128 <= payload[0] <= 128 + 7:
            self.engine_synced_frames.append(kind)
        if kind == "N" and self.stop_after_turns:
            self.turns_seen += 1
            if self.turns_seen >= self.stop_after_turns:
                self.stopped_early = True

    def run(self):
        srv = socket.socket()
        srv.bind(("127.0.0.1", 0))
        srv.listen(1)
        port = srv.getsockname()[1]
        srv.settimeout(60)

        os.makedirs(self.user_prefix, exist_ok=True)
        args = [self.engine, "--internal", "--port", str(port),
                "--prefix", self.data, "--user-prefix", self.user_prefix,
                "--locale", "en.txt", "--nosound", "--nomusic",
                "--width", "640", "--height", "480"] + self.extra_args
        proc = subprocess.Popen(args, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True)
        out_lines = []
        drain = threading.Thread(
            target=lambda: out_lines.extend(iter(proc.stdout.readline, "")),
            daemon=True)
        drain.start()

        deadline = time.time() + self.timeout
        try:
            conn, _ = srv.accept()
            conn.settimeout(30)
            buf = b""
            while True:
                if time.time() > deadline:
                    self.error = "timeout"
                    proc.kill()
                    break
                try:
                    chunk = conn.recv(65536)
                except socket.timeout:
                    self.error = "socket timeout"
                    break
                except ConnectionResetError:
                    # The engine died mid-session; its log says why.
                    self.error = self.error or "connection reset by engine"
                    break
                if not chunk:
                    break
                buf += chunk
                while buf and len(buf) > buf[0]:
                    n = buf[0]
                    payload, buf = buf[1:1 + n], buf[1 + n:]
                    if payload:
                        self._handle(conn, payload)
                if self.stopped_early:
                    break
        except socket.timeout:
            self.error = "engine never connected"
        finally:
            try:
                proc.wait(timeout=15)
            except subprocess.TimeoutExpired:
                proc.kill()
            srv.close()
        self.stdout = "".join(out_lines)
        return self

    def _handle(self, conn, payload):
        self._on_engine_frame(payload)
        kind = chr(payload[0])
        if kind == "?":
            b = frame("!")
            conn.sendall(b)
            self._on_frontend(b)
        elif kind == "C":
            b = self.raw_config if self.raw_config is not None else frame_all(self.config)
            conn.sendall(b)
            self._on_frontend(b)
        elif kind == "E":
            self.error = payload[1:].decode("utf-8", "replace").strip()
        elif kind == "i":
            self.stats.append((chr(payload[1]) if len(payload) > 1 else "?",
                               payload[2:].decode("utf-8", "replace")))
        elif kind == "q":
            self.finished = True
        elif kind == "Q":
            self.interrupted = True


def log_path(user_prefix):
    return os.path.join(user_prefix, "Logs", "game0.log")


def desync_count(user_prefix):
    p = log_path(user_prefix)
    if not os.path.exists(p):
        return None
    with open(p, errors="replace") as f:
        return sum(1 for line in f if "Desync detected" in line)


def turn_lines(user_prefix):
    """(tick, checksum) per 'Next turn', and the 'N' frames actually consumed."""
    p = log_path(user_prefix)
    turns, got_n = [], []
    if not os.path.exists(p):
        return turns, got_n
    with open(p, errors="replace") as f:
        for line in f:
            if "Next turn: time" in line:
                turns.append(line.strip())
            elif 'got cmd "N"' in line:
                got_n.append(line.strip())
    return turns, got_n


def fresh(work, name):
    d = os.path.join(work, name)
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(os.path.join(d, "Logs"), exist_ok=True)
    return d


def cmd_record(a):
    prefix = fresh(a.work, "record")
    cfg = config_commands(a.seed, a.turntime, a.health, a.hogs, a.difficulty)
    print(f"== Recording (seed={a.seed}, {a.hogs} hogs x2, turn {a.turntime}ms)")
    t0 = time.time()
    s = Session(a.engine, a.data, prefix, config=cfg, record=True,
                timeout=a.timeout, stop_after_turns=a.stop_after_turns).run()
    dt = time.time() - t0
    if s.error and not s.stopped_early:
        print(f"FAIL: engine error while recording: {s.error}")
        print(s.stdout[-2000:])
        return 1
    if not s.finished and not s.stopped_early:
        print(f"FAIL: match did not finish normally (q not received) after {dt:.0f}s")
        print(s.stdout[-2000:])
        return 1
    demo = bytes(s.demo)
    # DemosRepository.tlToTd: flip the leading game-type token, first hit only.
    assert demo.startswith(b"\x02TL"), demo[:8]
    demo = b"\x02TD" + demo[3:]
    with open(a.demo, "wb") as f:
        f.write(demo)
    turns, _ = turn_lines(prefix)
    print(f"OK: {len(demo)} bytes, {len(turns)} turns, {len(s.stats)} stat lines, "
          f"{dt:.0f}s -> {a.demo}")
    print(f"   log: {log_path(prefix)}")
    return 0


def _replay(a, name, extra_args, use_socket):
    prefix = fresh(a.work, name)
    with open(a.demo, "rb") as f:
        raw = f.read()
    t0 = time.time()
    if use_socket:
        s = Session(a.engine, a.data, prefix, raw_config=raw,
                    extra_args=extra_args, timeout=a.timeout).run()
        stdout, err = s.stdout, s.error
        emitted = s.engine_synced_frames
        kinds = "".join(sorted({k for k, _ in s.stats}))
        print(f"   stat frames: {len(s.stats)}"
              + (f" (kinds: {kinds})" if s.stats else "")
              + f", finished={s.finished}")
    else:
        proc = subprocess.run(
            [a.engine, a.demo, "--prefix", a.data, "--user-prefix", prefix,
             "--locale", "en.txt", "--nosound", "--nomusic"] + list(extra_args),
            capture_output=True, text=True, timeout=a.timeout)
        stdout, err, emitted = proc.stdout + proc.stderr, None, []
    dt = time.time() - t0
    n = desync_count(prefix)
    turns, got_n = turn_lines(prefix)
    print(f"== Replay [{name}] {dt:.0f}s: desync={n}, turns={len(turns)}, "
          f"'N' consumed={len(got_n)}"
          + (f", engine emitted {len(emitted)} synced frames" if use_socket else ""))
    if err:
        print(f"   engine error: {err}")
    if n:
        print("   -> DESYNC REPRODUCED")
    print(f"   log: {log_path(prefix)}")
    return prefix, n, stdout


def cmd_replay(a):
    # --stats-only replays headless and at full speed (uGame.pas gives a demo
    # a huge Lag budget in that mode), which is the quick way to ask "does a
    # replay reach the end, and does it report stats?".
    extra = ["--stats-only"] if a.fast else []
    prefix, _, _ = _replay(a, "replay-socket", extra, True)
    return 0


def cmd_file(a):
    _replay(a, "replay-file", [], False)
    return 0


def cmd_cycle(a):
    rc = cmd_record(a)
    if rc:
        return rc
    _replay(a, "replay-socket", [], True)
    _replay(a, "replay-file", [], False)
    print(f"\nDiff the logs:\n  diff {log_path(os.path.join(a.work, 'record'))} "
          f"{log_path(os.path.join(a.work, 'replay-socket'))} | head -50")
    return 0


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--engine", required=True)
    p.add_argument("--data", required=True)
    p.add_argument("--work", required=True)
    p.add_argument("--seed", default="{sync-loop-seed-1}")
    p.add_argument("--turntime", type=int, default=10000)
    p.add_argument("--health", type=int, default=100)
    p.add_argument("--hogs", type=int, default=2)
    p.add_argument("--difficulty", type=int, default=1)
    p.add_argument("--timeout", type=int, default=900)
    p.add_argument("--stop-after-turns", type=int, default=0,
                   help="cut the recording short after N end-of-turn frames")
    p.add_argument("--fast", action="store_true",
                   help="replay with --stats-only: headless and full speed")
    p.add_argument("mode", choices=("record", "replay", "file", "cycle"))
    p.add_argument("demo", nargs="?", default=None)
    a = p.parse_args()
    if a.demo is None:
        a.demo = os.path.join(a.work, "reference.hwd")
    return {"record": cmd_record, "replay": cmd_replay,
            "file": cmd_file, "cycle": cmd_cycle}[a.mode](a)


if __name__ == "__main__":
    sys.exit(main())
