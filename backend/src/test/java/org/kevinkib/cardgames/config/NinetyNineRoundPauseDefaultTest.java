package org.kevinkib.cardgames.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The round-end text must stay readable: the default pause leaves the bust overlay on screen for several seconds. */
class NinetyNineRoundPauseDefaultTest {

    /** The frontend shows the overlay this long after the round end (NINETY_NINE_TEMPO.bustRevealMs). */
    private static final long OVERLAY_REVEAL_DELAY_MS = 1500;

    @Test
    void givenTheDefaultPause_thenTheOverlayIsReadableForAtLeastSixSeconds() {
        long pause = Long.parseLong(NinetyNineConfig.DEFAULT_ROUND_PAUSE_MS);
        assertTrue(pause - OVERLAY_REVEAL_DELAY_MS >= 6000, "pause was " + pause + " ms");
    }
}
