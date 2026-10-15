package org.kevinkib.cardgames.mangola.domain;

/**
 * The rules of a game as data. Version 1 builds only {@link #OWNER} (the owner's game, as coded in
 * {@code ngola.js}), with the {@code endless} switch set by a host option; the other switches have one
 * value each and mark where the documented variants (Mangola 1952, Omweso, Igisoro, Kiela) will plug in.
 *
 * @param endless            what happens to a sowing that never ends
 * @param capture            how a capture is triggered
 * @param capturedSeedsFrom  where the captured seeds are sown from
 * @param captureTakesOwnPit whether a capture also takes the seeds of the capturing pit (the 1952 Mangola
 *                           does; not supported yet, must be false)
 * @param lastSeedInEmptyPit what a last seed does when the next pit is empty
 * @param minSeedsToMove     the seeds a pit needs to be sown
 */
public record MangolaRules(Endless endless, Capture capture, CapturedSeedsFrom capturedSeedsFrom,
                           boolean captureTakesOwnPit, LastSeedInEmptyPit lastSeedInEmptyPit,
                           int minSeedsToMove) {

    /** A sowing that never ends: not a legal move, or a move that ends the game in a draw (as in the JS). */
    public enum Endless { ILLEGAL, DRAW }

    /** Automatic: a capture happens whenever its condition holds, the player has no choice. */
    public enum Capture { AUTOMATIC }

    /** The captured seeds are sown from the pit after the start of the capturing lap. */
    public enum CapturedSeedsFrom { LAP_START }

    /** The last seed goes to the pit after the empty one and the turn ends (no relay, no capture). */
    public enum LastSeedInEmptyPit { NEXT_PIT_AND_STOP }

    public static final MangolaRules OWNER = new MangolaRules(Endless.ILLEGAL, Capture.AUTOMATIC,
            CapturedSeedsFrom.LAP_START, false, LastSeedInEmptyPit.NEXT_PIT_AND_STOP, 2);

    public MangolaRules {
        if (endless == null || capture == null || capturedSeedsFrom == null || lastSeedInEmptyPit == null) {
            throw new IllegalArgumentException("Every rule switch needs a value");
        }
        if (captureTakesOwnPit) {
            throw new IllegalArgumentException("A capture taking the capturing pit is not supported yet");
        }
        if (minSeedsToMove < 1) {
            throw new IllegalArgumentException("A pit needs at least one seed to be sown");
        }
    }

    public MangolaRules withEndless(Endless endless) {
        return new MangolaRules(endless, capture, capturedSeedsFrom, captureTakesOwnPit,
                lastSeedInEmptyPit, minSeedsToMove);
    }

}
