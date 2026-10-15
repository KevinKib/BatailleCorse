package org.kevinkib.cardgames.mangola.domain;

/**
 * One step of a move, in the order it happens. A trace applied to the previous board gives the new
 * board without any knowledge of the rules: {@link Pick} and {@link Relay} take {@code count} seeds out
 * of a pit, {@link Drop} adds one, {@link Capture} empties the facing and the behind pit,
 * {@link SkipEmpty} and {@link ResumeAfter} change nothing (they explain the next drop).
 */
public sealed interface SowingStep {

    /** The move starts: every seed of the chosen pit goes into the hand. */
    record Pick(int pit, int count) implements SowingStep {
    }

    /** One seed dropped into a pit. */
    record Drop(int pit) implements SowingStep {
    }

    /** The last seed would fall into this empty pit: it goes to the next pit instead and the turn ends. */
    record SkipEmpty(int pit) implements SowingStep {
    }

    /** The last seed fell on an inner pit with both the facing and the behind pit occupied: all taken. */
    record Capture(int fromPit, int facing, int behind, int count) implements SowingStep {
    }

    /** The last seed fell on an occupied pit: its {@code count} seeds (the last one included) are re-sown. */
    record Relay(int pit, int count) implements SowingStep {
    }

    /** After a capture the captured seeds are sown from the pit after this one, the start of the lap. */
    record ResumeAfter(int pit) implements SowingStep {
    }
}
