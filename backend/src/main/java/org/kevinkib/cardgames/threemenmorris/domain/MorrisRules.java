package org.kevinkib.cardgames.threemenmorris.domain;

/**
 * The rule set of a variant, as data: the geometry and the pieces each player holds. The engine reads
 * it; Tapatan, the game of v1, is the first instance; Achi is another.
 */
public record MorrisRules(Geometry geometry, int piecesPerPlayer) {

    public static final MorrisRules TAPATAN = new MorrisRules(Geometry.TAPATAN, 3);

    /** Four pieces each: eight pieces on nine points, one hole. */
    public static final MorrisRules ACHI = new MorrisRules(Geometry.TAPATAN, 4);

    public MorrisRules {
        if (geometry == null) {
            throw new IllegalArgumentException("A geometry is required");
        }
        if (piecesPerPlayer < 1) {
            throw new IllegalArgumentException("A player holds at least one piece");
        }
    }
}
