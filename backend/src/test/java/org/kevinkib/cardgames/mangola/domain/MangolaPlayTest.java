package org.kevinkib.cardgames.mangola.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MangolaPlayTest {

    private static final PlayerId SEAT_0 = new PlayerId(0);
    private static final PlayerId SEAT_1 = new PlayerId(1);

    private static Mangola game(String sideA, String sideB) {
        return Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0, Boards.of(sideA, sideB));
    }

    @Test
    void theStartingSeatIsSideAAndPlaysFirst() {
        Mangola game = game("a2:3 h1:2", "c3:2 d4:3");
        assertEquals(Phase.PLAY, game.phase());
        assertEquals(SEAT_0, game.seatA());
        assertEquals(SEAT_0, game.currentSeat());
        assertEquals(Side.A, game.sideOf(SEAT_0));
        assertEquals(Side.B, game.sideOf(SEAT_1));
        assertEquals(List.of(SEAT_0, SEAT_1), game.getPlayerIds());
        assertFalse(game.isFinished());
        assertEquals(MangolaResult.ONGOING, game.result());
        assertEquals(0, game.version());
    }

    @Test
    void sideAMayBeSeatOne() throws Exception {
        Mangola game = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_1,
                Boards.of("a2:3", "c3:2 d4:3"));
        assertEquals(SEAT_1, game.currentSeat());
        assertEquals(Side.A, game.sideOf(SEAT_1));
        assertThrows(NotPlayersTurnException.class, () -> game.sow(SEAT_0, Boards.pit("c3")));
        game.sow(SEAT_1, Boards.pit("a2"));
        assertEquals(SEAT_0, game.currentSeat());
    }

    @Test
    void aMoveSowsPassesTheTurnAndBumpsTheVersion() throws Exception {
        Mangola game = game("a2:3", "c3:2 d4:3");
        MangolaEvent.Sown event = game.sow(SEAT_0, Boards.pit("a2"));
        assertEquals(SEAT_0, event.seat());
        assertEquals(Boards.pit("a2"), event.pit());
        assertEquals(0, event.captured());
        assertEquals(Boards.of("b2:1 c2:1 e2:1", "c3:2 d4:3"), game.board());
        assertEquals(SEAT_1, game.currentSeat());
        assertEquals(1, game.version());
        assertEquals(Boards.pit("a2"), game.lastMove().pit());
        assertEquals(SEAT_0, game.lastMove().seat());
        assertEquals(event.trace(), game.lastMove().trace());
        assertEquals(Boards.of("b2:1 c2:1 e2:1", "c3:2 d4:3").toList(), game.position().board());
        assertEquals(1, game.position().toMove());
    }

    @Test
    void aMoveReportsTheSeedsItCaptured() throws Exception {
        Mangola game = game("a2:2 c2:1 h1:2", "c3:2 c4:3 a3:2");
        MangolaEvent.Sown event = game.sow(SEAT_0, Boards.pit("a2"));
        assertEquals(5, event.captured());
    }

    @Test
    void legalPitsAreOwnPitsWithTwoSeedsOrMoreAndOnlyForTheSeatToMove() {
        Mangola game = game("a2:3 b2:1 h1:2", "c3:2 d4:3");
        assertEquals(List.of(Boards.pit("h1"), Boards.pit("a2")), sorted(game.legalPits(SEAT_0)));
        assertEquals(List.of(), game.legalPits(SEAT_1));
    }

    private static List<Integer> sorted(List<Integer> pits) {
        return pits.stream().sorted().toList();
    }

    @Test
    void refusedMovesChangeNothing() throws Exception {
        Mangola game = game("a2:3 b2:1", "c3:2 d4:3");
        assertThrows(NotPlayersTurnException.class, () -> game.sow(SEAT_1, Boards.pit("c3")));
        assertThrows(NotYourPitException.class, () -> game.sow(SEAT_0, Boards.pit("c3")));
        assertThrows(TooFewSeedsException.class, () -> game.sow(SEAT_0, Boards.pit("b2")));
        assertThrows(TooFewSeedsException.class, () -> game.sow(SEAT_0, Boards.pit("h1")));
        assertThrows(InvalidPitException.class, () -> game.sow(SEAT_0, 32));
        assertThrows(InvalidPitException.class, () -> game.sow(SEAT_0, -1));
        assertEquals(0, game.version());
        assertEquals(SEAT_0, game.currentSeat());
        assertEquals(Boards.of("a2:3 b2:1", "c3:2 d4:3"), game.board());
        assertNull(game.lastMove());
    }

    @Test
    void aFinishedGameRefusesEveryMove() throws Exception {
        Mangola game = game("a2:3", "c3:2");
        game.forfeit(SEAT_1);
        assertThrows(FinishedGameException.class, () -> game.sow(SEAT_0, Boards.pit("a2")));
        assertEquals(List.of(), game.legalPits(SEAT_0));
    }

    @Test
    void theMoverWinsRightAfterAMoveThatLeavesTheOpponentWithoutALegalMove() throws Exception {
        Mangola game = game("a2:3", "c3:1 d4:1");
        game.sow(SEAT_0, Boards.pit("a2"));
        assertTrue(game.isFinished());
        assertEquals(SEAT_0, game.result().winner());
        assertEquals(MangolaResult.Reason.NO_MOVE, game.result().reason());
        assertFalse(game.result().isDraw());
        assertEquals(1, game.version());
    }

    @Test
    void aCaptureThatEmptiesTheOpponentWins() throws Exception {
        Mangola game = game("a2:2 c2:1", "c3:2 c4:3");
        game.sow(SEAT_0, Boards.pit("a2"));
        assertEquals(0, game.board().sideTotal(Side.B));
        assertEquals(SEAT_0, game.result().winner());
        assertEquals(MangolaResult.Reason.NO_MOVE, game.result().reason());
    }

    @Test
    void sideBWinsByTheSameRule() throws Exception {
        Mangola game = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0,
                Boards.of("g2:1 g1:1", "h4:2 g3:1"), SEAT_1);
        game.sow(SEAT_1, Boards.pit("h4"));
        assertEquals(SEAT_1, game.result().winner());
        assertEquals(MangolaResult.Reason.NO_MOVE, game.result().reason());
    }

    @Test
    void forfeitGivesTheGameToTheOtherSeatOnce() {
        Mangola game = game("a2:3", "c3:2");
        game.forfeit(SEAT_0);
        assertTrue(game.isFinished());
        assertEquals(SEAT_1, game.result().winner());
        assertEquals(MangolaResult.Reason.FORFEIT, game.result().reason());
        assertEquals(1, game.version());
        game.forfeit(SEAT_1);
        assertEquals(SEAT_1, game.result().winner());
        assertEquals(1, game.version());
    }

    @Test
    void forfeitByAnUnknownSeatIsIgnored() {
        Mangola game = game("a2:3", "c3:2");
        game.forfeit(new PlayerId(7));
        assertFalse(game.isFinished());
        assertEquals(0, game.version());
    }

    @Test
    void fromLayoutRefusesAFirstSeatThatIsNotASeat() {
        assertThrows(IllegalArgumentException.class, () -> Mangola.fromLayout(GameId.generate(),
                MangolaRules.OWNER, new PlayerId(2), MangolaBoard.empty()));
    }

    @Test
    void seedsNeverLeaveTheBoardOverRandomGames() throws Exception {
        Random random = new Random(42L);
        int moves = 0;
        for (int g = 0; g < 100; g++) {
            int[] counts = new int[MangolaGeometry.PITS];
            for (Side side : Side.values()) {
                List<Integer> loop = MangolaGeometry.loop(side);
                for (int s = 0; s < 32; s++) {
                    counts[loop.get(random.nextInt(16))]++;
                }
            }
            Mangola game = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0, MangolaBoard.of(counts));
            while (!game.isFinished()) {
                PlayerId seat = game.currentSeat();
                List<Integer> legal = game.legalPits(seat);
                assertFalse(legal.isEmpty());
                // every legal pit sows, every other own pit is refused
                for (int pit : MangolaGeometry.loop(game.sideOf(seat))) {
                    if (!legal.contains(pit)) {
                        int shot = pit;
                        assertThrows(MangolaException.class, () -> new Replica(game).sow(seat, shot));
                    }
                }
                game.sow(seat, legal.get(random.nextInt(legal.size())));
                moves++;
                assertEquals(64, game.board().total());
            }
        }
        assertTrue(moves > 100);
    }

    /** A throwaway copy of the position to try a refused move without touching the game under test. */
    private static final class Replica {
        private final Mangola copy;

        Replica(Mangola game) {
            this.copy = Mangola.fromLayout(GameId.generate(), game.rules(), game.seatA(), game.board(),
                    game.currentSeat());
        }

        void sow(PlayerId seat, int pit) throws MangolaException {
            copy.sow(seat, pit);
        }
    }
}
