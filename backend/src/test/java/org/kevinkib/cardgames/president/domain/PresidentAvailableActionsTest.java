package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cardgames.president.domain.rules.MatchLength;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;

import java.util.List;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/** What a seat may do now, from the domain: the DTO and the bots only read it. */
class PresidentAvailableActionsTest {

    private static PlayerId p(int seat) {
        return new PlayerId(seat);
    }

    @Test
    void theLeaderMayOnlyPlay() {
        President game = aPresident().withHand("4C 9C").withHand("5C").withHand("6C").leader(0).build();

        assertThat(game.availableActions(p(0)), contains(Action.PLAY));
    }

    @Test
    void aFollowerWithABeatingPlayMayPlayOrPass() throws Exception {
        President game = aPresident().withHand("4C 9C").withHand("5C 6C").withHand("7C").leader(0).build();
        game.play(p(0), cards("4C"));

        assertThat(game.availableActions(p(1)), contains(Action.PLAY, Action.PASS));
    }

    @Test
    void aFollowerWhoCannotBeatMayOnlyPass() throws Exception {
        President game = aPresident().withHand("9C 10C").withHand("5C 6C").withHand("7C 8C").leader(0).build();
        game.play(p(0), cards("9C"));

        assertThat(game.availableActions(p(1)), contains(Action.PASS));
    }

    @Test
    void aSeatThatIsNotToActHasNothingToDo() {
        President game = aPresident().withHand("4C 9C").withHand("5C").withHand("6C").leader(0).build();

        assertThat(game.availableActions(p(1)), is(empty()));
        assertThat(game.availableActions(p(2)), is(empty()));
    }

    @Test
    void aSeatThatWentOutHasNothingToDo() throws Exception {
        President game = aPresident().withHand("4C").withHand("5C 6C").withHand("7C 8C").leader(0).build();
        game.play(p(0), cards("4C"));

        assertThat(game.availableActions(p(0)), is(empty()));
    }

    @Test
    void duringTheExchangeOnlyTheHighTitlesWhoStillOweCardsMayGiveBack() throws Exception {
        President game = OrderedRounds.playedRoundOne(GameId.generate(), new RandomDeckSource(),
                PresidentOptions.defaults(), 1, 3, 0, 2);
        for (PlayerId seat : game.getPlayerIds()) {
            game.ready(seat);
        }

        // President seat 1 and Vice-President seat 3 owe cards; Vice-Scum seat 0 and Scum seat 2 do not
        assertThat(game.availableActions(p(1)), contains(Action.GIVE_BACK));
        assertThat(game.availableActions(p(3)), contains(Action.GIVE_BACK));
        assertThat(game.availableActions(p(0)), is(empty()));
        assertThat(game.availableActions(p(2)), is(empty()));

        game.giveBack(p(1), game.hand(p(1)).subList(0, 2));
        assertThat(game.availableActions(p(1)), is(empty()));
        assertThat(game.availableActions(p(3)), contains(Action.GIVE_BACK));
    }

    @Test
    void atTheBarrierEverySeatMayBeReadyUntilItIs() throws Exception {
        President game = OrderedRounds.playedRoundOne(GameId.generate(), new RandomDeckSource(),
                PresidentOptions.defaults(), 1, 3, 0, 2);

        assertThat(game.availableActions(p(0)), contains(Action.READY));
        assertThat(game.availableActions(p(2)), contains(Action.READY));
        game.ready(p(0));
        assertThat(game.availableActions(p(0)), is(empty()));
        assertThat(game.availableActions(p(2)), contains(Action.READY));
    }

    @Test
    void onceTheMatchIsFinishedNobodyHasAnything() throws Exception {
        President game = new President(GameId.generate(), 3, 4L, new RandomDeckSource(),
                PresidentOptions.defaults().withLength(MatchLength.ROUNDS_3));
        Random random = new Random(2);
        while (PresidentDriver.step(game, random)) {
            // play on
        }

        List<PlayerId> seats = game.getPlayerIds();
        for (PlayerId seat : seats) {
            assertThat(game.availableActions(seat), is(empty()));
        }
    }
}
