package org.kevinkib.cardgames.threemenmorris.domain;

/**
 * The rule set of a variant, as data: the geometry, the pieces each player holds and the placement
 * restriction. The engine reads it; Tapatan, the game of v1, is the only instance of v1; Achi and Picaria are two more.
 */
public record MorrisRules(Geometry geometry, int piecesPerPlayer, PlacementRule placement) {

    /** Which points may receive a piece during the placement phase. */
    public enum PlacementRule {
        /** Any free point. */
        FREE,
        /** Any free point, except the centre until each player has placed their first piece. */
        CENTRE_AFTER_FIRST_PIECES
    }

    /** The centre of the nine-point boards, row by row. */
    public static final int CENTRE = 4;

    public static final MorrisRules TAPATAN = new MorrisRules(Geometry.TAPATAN, 3, PlacementRule.FREE);

    /** Four pieces each: eight pieces on nine points, one hole. */
    public static final MorrisRules ACHI = new MorrisRules(Geometry.TAPATAN, 4, PlacementRule.FREE);

    public static final MorrisRules PICARIA = new MorrisRules(Geometry.PICARIA, 3,
            PlacementRule.CENTRE_AFTER_FIRST_PIECES);

    public MorrisRules {
        if (geometry == null || placement == null) {
            throw new IllegalArgumentException("Geometry and placement rule are required");
        }
        if (piecesPerPlayer < 1) {
            throw new IllegalArgumentException("A player holds at least one piece");
        }
    }
}
