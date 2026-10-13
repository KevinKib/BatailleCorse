package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Input validation of the queries, the test layouts, the factory and the records that carry a point. */
class ThreeMenMorrisInvariantsTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    private final ThreeMenMorris game = new ThreeMenMorris(GameId.generate());

    @Test
    void unknownSeatOrPointQueriesAreRefusedWithADomainException() {
        assertThrows(InvalidQueryException.class, () -> game.owner(9));
        assertThrows(InvalidQueryException.class, () -> game.owner(-1));
        assertThrows(InvalidQueryException.class, () -> game.piecesToPlace(new PlayerId(2)));
        assertThrows(InvalidQueryException.class, () -> game.piecesToPlace(new PlayerId(-1)));
        assertThrows(InvalidQueryException.class, () -> game.piecesToPlace(null));
    }

    @Test
    void theBoardIsExposedAsTheOwnerOfEachPoint() throws Exception {
        game.place(WHITE, 4);
        game.place(BLACK, 0);

        assertThat(game.board(), is(Arrays.asList(BLACK, null, null, null, WHITE, null, null, null, null)));
    }

    @Test
    void aLayoutMustHaveNinePointsAndThreePiecesPerPlayer() {
        GameId id = GameId.generate();
        assertThrows(IllegalArgumentException.class, () -> ThreeMenMorris.fromLayout(id, "W.B", 0, 0, WHITE));
        assertThrows(IllegalArgumentException.class,
                () -> ThreeMenMorris.fromLayout(id, "W.B" + ".WB" + "...", 0, 0, WHITE));
        assertThrows(IllegalArgumentException.class,
                () -> ThreeMenMorris.fromLayout(id, "WWW" + "WB." + "B.B", 0, 0, WHITE));
        assertThrows(IllegalArgumentException.class,
                () -> ThreeMenMorris.fromLayout(id, "W.B" + ".WB" + "W.B", 1, 0, WHITE));
        assertThrows(IllegalArgumentException.class,
                () -> ThreeMenMorris.fromLayout(id, "W.B" + ".WB" + "W.B", 0, 0, new PlayerId(2)));
    }

    @Test
    void aLayoutWithTheRightPiecesInBoardAndInHandIsAccepted() {
        ThreeMenMorris placement = ThreeMenMorris.fromLayout(GameId.generate(), "W.B" + ".W." + "...", 1, 2, BLACK);

        assertThat(placement.phase(), is(Phase.PLACEMENT));
        assertThat(placement.piecesToPlace(WHITE), is(1));
        assertThat(placement.piecesToPlace(BLACK), is(2));
    }

    @Test
    void theFactoryOnlyBuildsTwoPlayerGames() {
        ThreeMenMorrisFactory factory = new ThreeMenMorrisFactory();

        assertThrows(IllegalArgumentException.class, () -> factory.create(GameId.generate(), 3));
        assertThrows(IllegalArgumentException.class, () -> factory.create(GameId.generate(), 1));
    }

    @Test
    void recordsCarryingAPointRefuseNegativeValuesAndTheAggregateRefusesPointsOffItsBoard() {
        assertThrows(IllegalArgumentException.class, () -> new Move(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> new MorrisEvent.Moved(WHITE, -1, 0));
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate());
        assertThrows(InvalidPointException.class, () -> game.place(WHITE, 9));
        assertThat(new Move(0, 8).to(), is(8));
    }
}
