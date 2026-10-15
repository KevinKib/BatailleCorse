package org.kevinkib.cardgames.mangola.domain.bot;

import org.kevinkib.cardgames.mangola.domain.MangolaBoard;
import org.kevinkib.cardgames.mangola.domain.MangolaGeometry;
import org.kevinkib.cardgames.mangola.domain.MangolaPosition;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Side;
import org.kevinkib.cardgames.mangola.domain.Sowing;
import org.kevinkib.cardgames.mangola.domain.SowingResult;

import java.util.ArrayList;
import java.util.List;

/** What the bots share about moves: the legal pits of a side and the result of sowing one, from the domain rules. */
final class MangolaMoves {

    /** A legal move and what it does: the resulting board, the seeds it captured, whether it never ends. */
    record Move(int pit, SowingResult result) {
        MangolaBoard board() {
            return result.board();
        }

        boolean endless() {
            return result.endless();
        }

        int captured() {
            return result.captured();
        }
    }

    private MangolaMoves() {
    }

    static MangolaBoard boardOf(MangolaPosition position) {
        return MangolaBoard.of(position.board().stream().mapToInt(Integer::intValue).toArray());
    }

    static SowingResult play(MangolaBoard board, Side side, int pit, MangolaRules rules) {
        return Sowing.sow(board, side, pit, rules);
    }

    /**
     * The moves the side may play, in loop order: own pits with enough seeds, leaving out the endless sowings when
     * the rules make them illegal (when they end the game in a draw they are legal moves, flagged endless).
     */
    static List<Move> moves(MangolaBoard board, Side side, MangolaRules rules) {
        List<Move> moves = new ArrayList<>();
        for (int pit : MangolaGeometry.loop(side)) {
            if (board.seeds(pit) < rules.minSeedsToMove()) {
                continue;
            }
            SowingResult result = Sowing.sow(board, side, pit, rules);
            if (result.endless() && rules.endless() == MangolaRules.Endless.ILLEGAL) {
                continue;
            }
            moves.add(new Move(pit, result));
        }
        return moves;
    }

    static List<Integer> legalPits(MangolaBoard board, Side side, MangolaRules rules) {
        return moves(board, side, rules).stream().map(Move::pit).toList();
    }

    /** How many pits the side may sow. */
    static int mobility(MangolaBoard board, Side side, MangolaRules rules) {
        return moves(board, side, rules).size();
    }
}
