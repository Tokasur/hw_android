#!/usr/bin/env python3
"""Land-preview IPC harness.

Speaks the exact protocol of the Android frontend's PreviewConnection.kt:
listens on a loopback port, launches the engine with
  --internal --port <p> --prefix <data> --landpreview
sends the framed generation commands (trailing "!" is the pong the engine
waits for) and expects 256*128/8 + 1 = 4097 raw bytes back.
"""
import socket
import subprocess
import sys
import time

ENGINE, DATA = sys.argv[1], sys.argv[2]


def frame(msg: str) -> bytes:
    payload = msg.encode()
    assert len(payload) <= 255
    return bytes([len(payload)]) + payload


def main() -> int:
    srv = socket.socket()
    srv.bind(("127.0.0.1", 0))
    srv.listen(1)
    port = srv.getsockname()[1]
    srv.settimeout(30)

    proc = subprocess.Popen(
        [ENGINE, "--internal", "--port", str(port), "--prefix", DATA,
         "--landpreview", "--nosound", "--nomusic"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    try:
        conn, _ = srv.accept()
        conn.settimeout(30)
        for cmd in ("eseed {e2e-test-seed}",
                    "e$template_filter 0",
                    "e$mapgen 0",
                    "e$feature_size 50",
                    "!"):
            conn.sendall(frame(cmd))

        buf = b""
        while True:
            try:
                chunk = conn.recv(65536)
            except socket.timeout:
                break
            if not chunk:
                break
            buf += chunk

        mono = 256 * 128 // 8 + 1        # MOBILE build: TPreview, 1 bit/px
        alpha = 256 * 128 + 1            # desktop build: TPreviewAlpha, 8 bit/px
        if len(buf) not in (mono, alpha):
            print(f"FAIL: got {len(buf)} bytes, expected {mono} or {alpha}")
            return 1
        if len(buf) == mono:
            land = sum(bin(b).count("1") for b in buf[:-1])
        else:
            land = sum(1 for b in buf[:-1] if b)
        hog_limit = buf[-1]
        if land == 0:
            print("FAIL: preview is empty (no land generated)")
            return 1
        if not (1 <= hog_limit <= 64):
            print(f"FAIL: implausible hedgehog limit {hog_limit}")
            return 1
        kind = "mono/mobile" if len(buf) == mono else "alpha/desktop"
        print(f"OK: {kind} preview received ({land} land pixels, hog limit {hog_limit})")
        return 0
    finally:
        try:
            proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            proc.kill()
        srv.close()


if __name__ == "__main__":
    sys.exit(main())
