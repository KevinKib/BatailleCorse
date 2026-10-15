package org.kevinkib.cardgames.mangola.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The third occurrence of a position (32 pits and the seat to move) ends the game in a draw. */
class MangolaRepetitionTest {

    private static final PlayerId SEAT_0 = new PlayerId(0);
    private static final PlayerId SEAT_1 = new PlayerId(1);

    /**
     * A position that comes back after a cycle of 32 plies of the owner's rules, found once by a random
     * search (a throwaway program, 14 seeds a side): the first occurrence is after 5 plies, the second
     * after 37. {@link #MOVES} are the 37 pits sown, seat 0 first, alternating.
     */
    private static final String START =
            "a1:1 e1:1 f1:2 h1:1 d2:2 f2:1 h2:1 b3:1 d3:2 e3:1 g3:2 h3:1 a4:1 g4:1";
    private static final String MOVES =
            "f1 d3 e1 g3 d2 g4 h1 h4 g1 h3 f1 g3 e1 f3 d1 e3 c1 d3 b1 c3 a1 b3 a2 a3 b2 a4 c2 b4 d2 c4 e2 d4 f2 e4 g2 f4 h2";

    private static final int FIRST = 5;
    private static final int SECOND = 37;

    @Test
    void theThirdOccurrenceEndsTheGameInADraw() throws Exception {
        String[] moves = MOVES.split(" ");
        assertEquals(SECOND, moves.length);
        Mangola game = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0, Boards.of(START));
        assertEquals(1, game.repetitions(), "the starting position counts once");

        for (int ply = 0; ply < SECOND; ply++) {
            game.sow(ply % 2 == 0 ? SEAT_0 : SEAT_1, Boards.pit(moves[ply]));
            assertFalse(game.isFinished(), "ply " + (ply + 1));
            if (ply + 1 == FIRST) {
                assertEquals(1, game.repetitions());
            }
        }
        assertEquals(2, game.repetitions(), "second occurrence of the position of ply " + FIRST);

        // One more turn of the same cycle: moves FIRST..SECOND-1 again, from the second occurrence.
        int cycle = SECOND - FIRST;
        for (int i = 0; i < cycle; i++) {
            int ply = SECOND + i;
            assertFalse(game.isFinished(), "ply " + ply);
            game.sow(ply % 2 == 0 ? SEAT_0 : SEAT_1, Boards.pit(moves[FIRST + i]));
        }
        assertTrue(game.isFinished());
        assertNull(game.result().winner());
        assertEquals(MangolaResult.Reason.REPETITION, game.result().reason());
        assertTrue(game.result().isDraw());
        assertEquals(0, game.repetitions(), "nothing to count once the game is finished");
    }

    @Test
    void theSameBoardWithAnotherSeatToMoveIsAnotherPosition() throws Exception {
        MangolaBoard board = Boards.of("a2:2 h2:1", "a3:2 h4:1");
        Mangola a = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0, board, SEAT_0);
        Mangola b = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0, board, SEAT_1);
        assertFalse(a.position().key().equals(b.position().key()));
        assertEquals(a.position().key(), a.position().key());
    }
}
