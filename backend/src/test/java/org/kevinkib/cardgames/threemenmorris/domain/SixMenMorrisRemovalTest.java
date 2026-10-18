package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.threemenmorris.domain.SixGames.BLACK;
import static org.kevinkib.cardgames.threemenmorris.domain.SixGames.WHITE;
import static org.kevinkib.cardgames.threemenmorris.domain.SixGames.points;
import static org.kevinkib.cardgames.threemenmorris.domain.SixGames.six;

/** The removal step: who may take what, the protected mills, and what a removal changes. */
class SixMenMorrisRemovalTest {

    /** White closes 0 1 2 by placing on 2; black holds the mill 8 9 10 and a piece on 12. */
    private ThreeMenMorris whiteToCloseAgainstAProtectedMill() {
        return six(points(0, 1), points(8, 9, 10, 12), WHITE);
    }

    @Test
    void removeEventNamesTheSeatAndThePointAndPassesTheTurn() throws Exception {
        ThreeMenMorris game = six(points(0, 1), points(8, 12), WHITE);
        game.place(WHITE, 2);
        int version = game.version();

        MorrisEvent event = game.remove(WHITE, 8);

        assertThat(event, is(new MorrisEvent.Removed(WHITE, 8)));
        assertThat(game.owner(8), nullValue());
        assertThat(game.version(), is(version + 1));
        assertThat(game.pendingRemoval(), nullValue());
        assertThat(game.lastMill(), is(List.of()));
        assertThat(game.currentSeat(), is(BLACK));
        assertThat(game.lost(BLACK), is(1));
        assertThat(game.piecesLeft(BLACK), is(5));
        assertThat(game.availableActions(BLACK), is(List.of(Action.PLACE)));
    }

    @Test
    void removingWithoutAPendingRemovalIsRefused() {
        ThreeMenMorris game = six(points(0, 1), points(8, 12), WHITE);
        int version = game.version();

        assertThrows(NoRemovalPendingException.class, () -> game.remove(WHITE, 8));

        assertThat(game.version(), is(version));
    }

    @Test
    void onlyTheSeatThatClosedTheMillMayRemove() throws Exception {
        ThreeMenMorris game = six(points(0, 1), points(8, 12), WHITE);
        game.place(WHITE, 2);

        assertThrows(NotPlayersTurnException.class, () -> game.remove(BLACK, 0));
    }

    @Test
    void anOwnPieceAnEmptyPointAndAnUnknownPointAreRefused() throws Exception {
        ThreeMenMorris game = six(points(0, 1), points(8, 12), WHITE);
        game.place(WHITE, 2);
        int version = game.version();

        assertThrows(NotOpponentPieceException.class, () -> game.remove(WHITE, 0));
        assertThrows(NotOpponentPieceException.class, () -> game.remove(WHITE, 5));
        assertThrows(InvalidPointException.class, () -> game.remove(WHITE, 16));
        assertThrows(InvalidPointException.class, () -> game.remove(WHITE, -1));

        assertThat(game.version(), is(version));
        assertThat(game.pendingRemoval(), is(WHITE));
    }

    @Test
    void aPieceInAMillIsProtectedWhileAnotherOpponentPieceIsNot() throws Exception {
        ThreeMenMorris game = whiteToCloseAgainstAProtectedMill();
        game.place(WHITE, 2);

        assertThat(game.legalRemovals(WHITE), is(List.of(12)));
        assertThrows(PieceInMillException.class, () -> game.remove(WHITE, 9));
        assertThat(game.owner(9), is(BLACK));

        game.remove(WHITE, 12);

        assertThat(game.owner(12), nullValue());
    }

    @Test
    void whenEveryOpponentPieceIsInAMillAnyOfThemCanBeTaken() throws Exception {
        ThreeMenMorris game = six(points(0, 1), points(8, 9, 10), WHITE);
        game.place(WHITE, 2);

        assertThat(game.legalRemovals(WHITE), is(List.of(8, 9, 10)));

        game.remove(WHITE, 9);

        assertThat(game.owner(9), nullValue());
        assertThat(game.lost(BLACK), is(1));
    }

    @Test
    void aPieceInTwoMillsOfAnotherSquareIsProtectedToo() throws Exception {
        // Black holds the corner 10 of two mills (8 9 10 and 10 11 12) and a stray piece on 4.
        ThreeMenMorris game = six(points(0, 1), points(8, 9, 10, 11, 12, 4), WHITE);
        game.place(WHITE, 2);

        assertThat(game.legalRemovals(WHITE), is(List.of(4)));
    }

    @Test
    void aDoubleMillGivesOneRemovalOnly() throws Exception {
        // White 0 1 3 4 then 2 closes both 0 1 2 and 2 3 4.
        ThreeMenMorris game = six(points(0, 1, 3, 4), points(9, 13), WHITE);

        game.place(WHITE, 2);
        game.remove(WHITE, 9);

        assertThat(game.pendingRemoval(), nullValue());
        assertThat(game.currentSeat(), is(BLACK));
        assertThat(game.lost(BLACK), is(1));
    }

    @Test
    void aRemovalThatLeavesTwoPiecesFinishesTheGameAsReduced() throws Exception {
        // Black holds three pieces in total (board plus hand): white takes one, two are left.
        ThreeMenMorris game = SixGames.game(MorrisRules.SIX_MEN, points(0, 1), points(8, 12), WHITE, 6, 3);
        game.place(WHITE, 2);

        MorrisEvent event = game.remove(WHITE, 8);

        assertThat(event, is(new MorrisEvent.Removed(WHITE, 8)));
        assertThat(game.isFinished(), is(true));
        assertThat(game.phase(), is(Phase.FINISHED));
        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.REDUCED)));
        assertThat(game.availableActions(WHITE), is(List.of()));
        assertThat(game.lost(BLACK), is(4));
        assertThat(game.winningLine(), is(List.of()));
    }

    @Test
    void anAcceptedRemovalCannotBeFollowedByAnotherAfterTheGameEnds() throws Exception {
        ThreeMenMorris game = SixGames.game(MorrisRules.SIX_MEN, points(0, 1), points(8, 12), WHITE, 6, 3);
        game.place(WHITE, 2);
        game.remove(WHITE, 8);

        assertThrows(FinishedGameException.class, () -> game.remove(WHITE, 12));
    }

    @Test
    void aSeatLeftWithoutAMoveAfterARemovalLoses() throws Exception {
        // Black 0 1 2 is a mill (protected) walled in by white 3 7 9; its fourth piece, on 10, is the only
        // one that can move. White closes 12 13 14 with 15 -> 14 and takes it.
        ThreeMenMorris game = SixGames.game(MorrisRules.SIX_MEN, points(3, 7, 9, 12, 13, 15), points(0, 1, 2, 10),
                WHITE, 6, 4);

        game.move(WHITE, 15, 14);
        assertThat(game.legalRemovals(WHITE), is(List.of(10)));
        game.remove(WHITE, 10);

        assertThat(game.result(), is(new MorrisResult(WHITE, MorrisResult.Reason.BLOCKED)));
        assertThat(game.isFinished(), is(true));
    }

    @Test
    void flyingUnderAtThreeLetsAThreePieceSeatMoveToAnyFreePoint() throws Exception {
        ThreeMenMorris game = SixGames.game(SixGames.sixWithFlying(), points(0, 5, 10), points(2, 4, 8, 12, 13, 15),
                WHITE, 3, 6);

        assertThat(game.legalMoves(WHITE).size(), is(3 * 7));
        assertThat(game.legalMoves(WHITE).contains(new Move(0, 13)), is(false)); // 13 is taken
        game.move(WHITE, 0, 14);

        assertThat(game.owner(14), is(WHITE));
    }

    @Test
    void flyingUnderAtThreeDoesNotApplyToAFourPieceSeat() {
        ThreeMenMorris game = SixGames.game(SixGames.sixWithFlying(), points(0, 5, 10, 11), points(2, 4, 8, 12, 13, 15),
                WHITE, 4, 6);

        assertThrows(NotAdjacentException.class, () -> game.move(WHITE, 0, 14));
    }

    @Test
    void aFlyingSeatIsNotBlockedWhileAPointIsFree() throws Exception {
        // Three white pieces walled in on 0 1 2 would be blocked without flying; with flying the game goes on.
        ThreeMenMorris game = SixGames.game(SixGames.sixWithFlying(), points(0, 1, 3), points(7, 9, 2, 4, 11, 8),
                BLACK, 3, 6);

        game.move(BLACK, 8, 15);

        assertThat(game.isFinished(), is(false));
        assertThat(game.currentSeat(), is(WHITE));
    }
}
