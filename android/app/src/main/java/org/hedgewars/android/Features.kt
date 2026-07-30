package org.hedgewars.android

/** Switches for work that is finished but not fit to ship yet. */
object Features {

    /**
     * Replay recording and playback (engine demos, *.hwd).
     *
     * Off for now: a recorded match plays back faithfully at first, but the
     * engine reports a state-checksum mismatch at every replayed turn
     * ("Desync detected", uCommandHandlers.pas chNextTurn) and the match
     * slowly drifts, so a replay can end differently from the match it came
     * from. Until that is understood, the whole feature stays out of sight.
     *
     * Setting this to true is all it takes to bring it back: the recorder in
     * GameActivity, the "save replay" button on the results screen and the
     * Replays entry in the main menu all key off this flag, and the "replays"
     * route is registered either way.
     */
    const val REPLAYS = false
}
