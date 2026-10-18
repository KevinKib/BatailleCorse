package org.kevinkib.cardgames.threemenmorris.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Arrays;

/** Test helpers to write Six Men's Morris positions: points are listed, not drawn as 16-character strings. */
public final class SixGames {

    public static final PlayerId WHITE = new PlayerId(0);
    public static final PlayerId BLACK = new PlayerId(1);

    private SixGames() {
    }

    /** A 16-character layout with W on the first points and B on the second ones. */
    public static String layout(int[] whites, int[] blacks) {
        char[] cells = new char[16];
        Arrays.fill(cells, '.');
        for (int point : whites) {
            cells[point] = 'W';
        }
        for (int point : blacks) {
            cells[point] = 'B';
        }
        return new String(cells);
    }

    public static int[] points(int... points) {
        return points;
    }

    /** A game of the given rules where each player holds six pieces, on the board plus in hand. */
    public static ThreeMenMorris game(MorrisRules rules, int[] whites, int[] blacks, PlayerId current) {
        return game(rules, whites, blacks, current, 6, 6);
    }

    /** Like {@link #game} for players who hold another number of pieces (pieces lost earlier). */
    public static ThreeMenMorris game(MorrisRules rules, int[] whites, int[] blacks, PlayerId current,
                               int whitePieces, int blackPieces) {
        return game(GameId.generate(), rules, whites, blacks, current, whitePieces, blackPieces);
    }

    /** Like {@link #game(MorrisRules, int[], int[], PlayerId, int, int)} for a game with a chosen id (wire tests). */
    public static ThreeMenMorris game(GameId id, MorrisRules rules, int[] whites, int[] blacks, PlayerId current,
                               int whitePieces, int blackPieces) {
        return ThreeMenMorris.fromLayout(id, rules, layout(whites, blacks),
                whitePieces - whites.length, blackPieces - blacks.length, whitePieces, blackPieces, current);
    }

    public static ThreeMenMorris six(int[] whites, int[] blacks, PlayerId current) {
        return six(GameId.generate(), whites, blacks, current);
    }

    public static ThreeMenMorris six(GameId id, int[] whites, int[] blacks, PlayerId current) {
        return game(id, MorrisRules.SIX_MEN, whites, blacks, current, 6, 6);
    }

    public static ThreeMenMorris freshSix() {
        return new ThreeMenMorris(GameId.generate(), MorrisRules.SIX_MEN, WHITE);
    }

    /** Six Men's Morris with a quiet-ply limit of the test's choosing. */
    public static MorrisRules sixWithQuietLimit(int limit) {
        return new MorrisRules(Geometry.SIX_MEN, 6, MorrisRules.LineRule.MILL, MorrisRules.RemovalRule.PROTECT_MILLS,
                MorrisRules.FlyingRule.NEVER, limit);
    }

    public static MorrisRules sixWithFlying() {
        return new MorrisRules(Geometry.SIX_MEN, 6, MorrisRules.LineRule.MILL, MorrisRules.RemovalRule.PROTECT_MILLS,
                MorrisRules.FlyingRule.AT_THREE, 50);
    }

    /**
     * Plays quiet plies on a game of lone pieces ({@link #twoLonePieces}): each ply is a legal move that leads to the
     * least seen position (never a third occurrence), so no repetition draw interferes with the quiet-ply limit.
     */
    public static final class Walker {

        private final ThreeMenMorris game;
        private final java.util.Map<MorrisPosition.Key, Integer> seen = new java.util.HashMap<>();

        public Walker(ThreeMenMorris game) {
            this.game = game;
            seen.merge(game.position().key(), 1, Integer::sum);
        }

        public void play(int plies) throws ThreeMenMorrisException {
            for (int i = 0; i < plies; i++) {
                PlayerId seat = game.currentSeat();
                Move chosen = null;
                int fewest = 2; // never walk into a third occurrence
                for (Move move : game.legalMoves(seat)) {
                    int count = seen.getOrDefault(after(seat, move), 0);
                    if (count < fewest) {
                        chosen = move;
                        fewest = count;
                    }
                }
                if (chosen == null) {
                    throw new IllegalStateException("Every move leads to a third occurrence");
                }
                game.move(seat, chosen.from(), chosen.to());
                seen.merge(game.position().key(), 1, Integer::sum);
            }
        }

        private MorrisPosition.Key after(PlayerId seat, Move move) {
            java.util.List<Integer> board = new java.util.ArrayList<>(game.position().board());
            board.set(move.to(), board.get(move.from()));
            board.set(move.from(), null);
            return new MorrisPosition.Key(board, 1 - seat.id());
        }
    }

    /** White on 0 and Black on 8, one piece each: the start of a {@link Walker}. */
    public static ThreeMenMorris twoLonePieces(MorrisRules rules) {
        return twoLonePieces(GameId.generate(), rules);
    }

    public static ThreeMenMorris twoLonePieces(GameId id, MorrisRules rules) {
        return game(id, rules, points(0), points(8), WHITE, 1, 1);
    }
}
