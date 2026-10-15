package org.kevinkib.cardgames.mangola.domain.bot;

import org.kevinkib.cardgames.mangola.domain.MangolaBoard;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Side;

import java.util.List;

/**
 * An independent oracle for the search tests: brute force on the domain moves, with no evaluation and no
 * pruning. Side A moves first.
 */
final class WinInThreeOracle {

    private static final MangolaRules RULES = MangolaRules.OWNER;

    private WinInThreeOracle() {
    }

    /** Whether side B has no move after side A played the pit (a win in one ply). */
    static boolean winsAtOnce(MangolaBoard board, int pit) {
        return MangolaMoves.moves(afterFirst(board, pit), Side.B, RULES).isEmpty();
    }

    /**
     * Whether playing the pit forces a win by the third ply: whatever side B answers, side A has a move after
     * which side B has none. A win in one ply does not count.
     */
    static boolean forcesWinInThree(MangolaBoard board, int pit) {
        MangolaBoard afterFirst = afterFirst(board, pit);
        List<MangolaMoves.Move> replies = MangolaMoves.moves(afterFirst, Side.B, RULES);
        if (replies.isEmpty()) {
            return false;
        }
        for (MangolaMoves.Move reply : replies) {
            boolean answered = MangolaMoves.moves(reply.board(), Side.A, RULES).stream()
                    .anyMatch(second -> MangolaMoves.moves(second.board(), Side.B, RULES).isEmpty());
            if (!answered) {
                return false;
            }
        }
        return true;
    }

    private static MangolaBoard afterFirst(MangolaBoard board, int pit) {
        return MangolaMoves.moves(board, Side.A, RULES).stream()
                .filter(move -> move.pit() == pit).findFirst().orElseThrow().board();
    }
}
