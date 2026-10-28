package org.kevinkib.cardgames.draughts.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.kevinkib.cardgames.draughts.domain.DraughtsResult.Reason;

/**
 * The automatic draws of FMJD article 6, as an immutable value fed one move at a time:
 * <ul>
 *   <li>6.1 the same position (men and kings told apart, same side to move) for the third time;</li>
 *   <li>6.2 25 moves of each player (50 plies) with only kings moved and no capture;</li>
 *   <li>6.3 three kings, two kings and a man, or a king and two men against a lone king: 16 more moves each
 *       (32 plies);</li>
 *   <li>6.4 (Annexe 1) two kings, a king and a man, or a king, against a lone king: 5 more moves each (10 plies).</li>
 * </ul>
 * The 6.3 / 6.4 count starts at the first position whose material matches, restarts when a capture changes the
 * material to the other case, and is not reset by a man move (assumption A5 of the spec). A move that moves a man
 * or captures can never be undone, so it also clears the repetition memory.
 */
public final class DrawTracker {

    public static final int REPETITIONS_FOR_DRAW = 3;
    public static final int KINGS_ONLY_PLIES = 50;
    public static final int THREE_AGAINST_ONE_PLIES = 32;
    public static final int TWO_AGAINST_ONE_PLIES = 10;

    private final Map<String, Integer> occurrences;
    private final String currentKey;
    private final int kingOnlyPlies;
    private final int endgamePlies;
    private final int endgameLimitPlies;

    private DrawTracker(Map<String, Integer> occurrences, String currentKey, int kingOnlyPlies, int endgamePlies,
            int endgameLimitPlies) {
        this.occurrences = occurrences;
        this.currentKey = currentKey;
        this.kingOnlyPlies = kingOnlyPlies;
        this.endgamePlies = endgamePlies;
        this.endgameLimitPlies = endgameLimitPlies;
    }

    /** The tracker of a game that starts from {@code position}. */
    public static DrawTracker start(DraughtsPosition position) {
        return new DrawTracker(Map.of(position.key(), 1), position.key(), 0, 0, limitFor(position));
    }

    /** The tracker after {@code move} was played from {@code before}, giving {@code after}. */
    public DrawTracker record(DraughtsPosition before, DraughtsMove move, DraughtsPosition after) {
        Piece mover = before.pieceAt(move.from());
        boolean irreversible = move.isCapture() || (mover != null && !mover.isKing());

        Map<String, Integer> next = irreversible ? new HashMap<>() : new HashMap<>(occurrences);
        next.merge(after.key(), 1, Integer::sum);

        int limit = limitFor(after);
        int endgame = limit == 0 || limit != endgameLimitPlies ? 0 : endgamePlies + 1;
        return new DrawTracker(Map.copyOf(next), after.key(), irreversible ? 0 : kingOnlyPlies + 1, endgame, limit);
    }

    /** The reason of an automatic draw if one applies now, in the order repetition, short ending, kings only. */
    public Optional<Reason> drawReason() {
        if (repetitionsOfCurrent() >= REPETITIONS_FOR_DRAW) {
            return Optional.of(Reason.DRAW_REPETITION);
        }
        if (endgameLimitPlies > 0 && endgamePlies >= endgameLimitPlies) {
            return Optional.of(Reason.DRAW_ENDGAME);
        }
        if (kingOnlyPlies >= KINGS_ONLY_PLIES) {
            return Optional.of(Reason.DRAW_KINGS_ONLY);
        }
        return Optional.empty();
    }

    /** How many times {@code position} has occurred since the last irreversible move (0 if never). */
    public int repetitions(DraughtsPosition position) {
        return occurrences.getOrDefault(position.key(), 0);
    }

    /** Consecutive plies with only kings moved and no capture (6.2). */
    public int kingOnlyPlies() {
        return kingOnlyPlies;
    }

    /** Plies played since the current 6.3 / 6.4 material case began; 0 when no case applies. */
    public int endgamePlies() {
        return endgamePlies;
    }

    /** The ply limit of the current 6.3 / 6.4 case: 32 or 10; 0 when no case applies. */
    public int endgameLimitPlies() {
        return endgameLimitPlies;
    }

    private int repetitionsOfCurrent() {
        return occurrences.getOrDefault(currentKey, 0);
    }

    /** 32 for the 6.3 material, 10 for the 6.4 material, 0 otherwise. */
    private static int limitFor(DraughtsPosition position) {
        int[] pieces = new int[2];
        int[] kings = new int[2];
        for (int square = 1; square <= BoardGeometry.SQUARES; square++) {
            Piece piece = position.pieceAt(square);
            if (piece != null) {
                pieces[piece.colour().ordinal()]++;
                if (piece.isKing()) {
                    kings[piece.colour().ordinal()]++;
                }
            }
        }
        for (Colour lone : Colour.values()) {
            Colour other = lone.opposite();
            if (pieces[lone.ordinal()] == 1 && kings[lone.ordinal()] == 1
                    && kings[other.ordinal()] >= 1 && pieces[other.ordinal()] <= 3) {
                return pieces[other.ordinal()] == 3 ? THREE_AGAINST_ONE_PLIES : TWO_AGAINST_ONE_PLIES;
            }
        }
        return 0;
    }
}
