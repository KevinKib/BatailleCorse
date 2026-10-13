package org.kevinkib.cardgames.threemenmorris.domain;

/**
 * The rule set of a variant, as data: the geometry, the pieces each player holds and the placement
 * restriction. The engine reads it; Tapatan, the game of v1, is the only instance for now.
 */
public record MorrisRules(Geometry geometry, int piecesPerPlayer, PlacementRule placement) {

    /** Which points may receive a piece during the placement phase. */
    public enum PlacementRule {
        /** Any free point. */
        FREE
    }

    public static final MorrisRules TAPATAN = new MorrisRules(Geometry.TAPATAN, 3, PlacementRule.FREE);

    public MorrisRules {
        if (geometry == null || placement == null) {
            throw new IllegalArgumentException("Geometry and placement rule are required");
        }
        if (piecesPerPlayer < 1) {
            throw new IllegalArgumentException("A player holds at least one piece");
        }
    }
}
