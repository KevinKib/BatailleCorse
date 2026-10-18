package org.kevinkib.cardgames.threemenmorris.domain;

/**
 * The rule set of a variant, as data: the geometry, the pieces each player holds, what a line of three
 * does, which opponent pieces a mill may take, whether a player may fly and the quiet-move limit. The
 * engine reads it; Tapatan, the game of v1, is the first instance; Achi and Six Men's Morris are others.
 */
public record MorrisRules(Geometry geometry, int piecesPerPlayer, LineRule lineRule, RemovalRule removal,
                          FlyingRule flying, int quietPlyLimit) {

    /** What a line of three own pieces does. */
    public enum LineRule {
        WIN, MILL
    }

    /** Which opponent pieces a mill may take. */
    public enum RemovalRule {
        PROTECT_MILLS
    }

    /** Whether a player reduced to three pieces may move to any free point. */
    public enum FlyingRule {
        NEVER, AT_THREE
    }

    public static final MorrisRules TAPATAN = new MorrisRules(Geometry.TAPATAN, 3);

    /** Four pieces each: eight pieces on nine points, one hole. */
    public static final MorrisRules ACHI = new MorrisRules(Geometry.TAPATAN, 4);

    /** Six Men's Morris: a line is a mill that removes a piece; a player reduced to two pieces loses. */
    public static final MorrisRules SIX_MEN = new MorrisRules(Geometry.SIX_MEN, 6, LineRule.MILL,
            RemovalRule.PROTECT_MILLS, FlyingRule.NEVER, 50);

    /** Rules where a line wins the game (the 3x3 variants), with no flying and no quiet-move limit. */
    public MorrisRules(Geometry geometry, int piecesPerPlayer) {
        this(geometry, piecesPerPlayer, LineRule.WIN, RemovalRule.PROTECT_MILLS, FlyingRule.NEVER, 0);
    }

    public MorrisRules {
        if (geometry == null) {
            throw new IllegalArgumentException("A geometry is required");
        }
        if (lineRule == null || removal == null || flying == null) {
            throw new IllegalArgumentException("Line, removal and flying rules are required");
        }
        if (piecesPerPlayer < 1) {
            throw new IllegalArgumentException("A player holds at least one piece");
        }
        if (lineRule == LineRule.MILL && piecesPerPlayer < 3) {
            throw new IllegalArgumentException("A mill needs at least three pieces per player");
        }
        if (quietPlyLimit < 0) {
            throw new IllegalArgumentException("The quiet-move limit cannot be negative");
        }
    }

    /**
     * True when the in-app retrograde solver may run on these rules: the {@code WIN} rules, whose state space
     * is a few thousand positions. {@code MILL} rules (Six Men's Morris, about 50 million positions) are not.
     */
    public boolean solvable() {
        return lineRule == LineRule.WIN;
    }
}
