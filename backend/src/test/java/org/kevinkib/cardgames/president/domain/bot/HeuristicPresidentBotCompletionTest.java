package org.kevinkib.cardgames.president.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;

import java.util.List;
import java.util.random.RandomGenerator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;
import static org.kevinkib.cardgames.president.domain.bot.PresidentBotFixtures.ranks;

/**
 * The bot and the equal-play binding (spec section 10): candidates under the binding are the matching sets and a
 * pass, the completion of the four is taken (in turn or out of turn), the easy level takes it half of the time and
 * the hard level may keep the cards that would be its forbidden finish.
 */
class HeuristicPresidentBotCompletionTest {

    private static final RandomGenerator ALWAYS_LOW = () -> 0L;
    private static final RandomGenerator ALWAYS_HIGH = () -> -1L;

    private static PlayerId seat(int id) {
        return new PlayerId(id);
    }

    private static PresidentBotDecision decide(PresidentBotLevel level, RandomGenerator random, PresidentBotView view) {
        return new HeuristicPresidentBot(level.tuning(), random)
                .decide(view, new PresidentBotMemory(view.seat()));
    }

    private static void play(President game, int seat, String codes) throws Exception {
        game.play(seat(seat), cards(codes));
    }

    /** A 9S, B 9H played, C (seat 2) bound to a 9 and holding the two missing 9s. */
    private static President cBoundHoldingTheCompletion() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 3C").withHand("9H 4C").withHand("9D 9C 5C").withHand("6C 7C").build();
        play(game, 0, "9S");
        play(game, 1, "9H");
        return game;
    }

    @Test
    void aBotThatLeadsAQuadPlaysItAndTheGameClosesTheTrick() throws Exception {
        President game = aPresident().leader(0)
                .withHand("5S 5H 5D 5C").withHand("KC 4C").withHand("QC 6C").build();
        PresidentBotView view = PresidentBotView.forSeat(game, seat(0));

        PresidentBotDecision decision = decide(PresidentBotLevel.HARD, ALWAYS_LOW, view);

        assertThat(decision, instanceOf(PresidentBotDecision.Play.class));
        assertThat(((PresidentBotDecision.Play) decision).cards(), containsInAnyOrder(cards("5S 5H 5D 5C").toArray()));
        game.play(seat(0), ((PresidentBotDecision.Play) decision).cards());
        assertThat(game.trick().plays().isEmpty(), is(true));
    }

    @Test
    void aBoundBotThatHoldsTheCompletionCompletesRatherThanMatchingOneCard() throws Exception {
        President game = cBoundHoldingTheCompletion();
        PresidentBotView view = PresidentBotView.forSeat(game, seat(2));

        assertThat(view.can(Action.COMPLETE), is(true));
        for (PresidentBotLevel level : new PresidentBotLevel[] {PresidentBotLevel.MEDIUM, PresidentBotLevel.HARD}) {
            PresidentBotDecision decision = decide(level, ALWAYS_HIGH, view);

            assertThat(decision, instanceOf(PresidentBotDecision.Complete.class));
            assertThat(((PresidentBotDecision.Complete) decision).cards(),
                    containsInAnyOrder(cards("9D 9C").toArray()));
        }
    }

    @Test
    void aBotOutOfTurnCompletesWhenOnlyTheCompletionIsOnOffer() throws Exception {
        President game = cBoundHoldingTheCompletion();
        game.pass(seat(2));
        PresidentBotView view = PresidentBotView.forSeat(game, seat(2));

        assertThat(view.actions(), is(List.of(Action.COMPLETE)));
        PresidentBotDecision decision = decide(PresidentBotLevel.MEDIUM, ALWAYS_HIGH, view);

        assertThat(decision, instanceOf(PresidentBotDecision.Complete.class));
    }

    @Test
    void theEasyLevelTakesTheCompletionOnlyHalfOfTheTime() throws Exception {
        President game = cBoundHoldingTheCompletion();
        game.pass(seat(2));
        PresidentBotView view = PresidentBotView.forSeat(game, seat(2));

        assertThat(decide(PresidentBotLevel.EASY, ALWAYS_LOW, view), instanceOf(PresidentBotDecision.Complete.class));
        assertThat(decide(PresidentBotLevel.EASY, ALWAYS_HIGH, view), instanceOf(PresidentBotDecision.Keep.class));
    }

    @Test
    void aBotThatDeclinesOutOfTurnKeepsTheCardsAndDoesNothing() throws Exception {
        President game = cBoundHoldingTheCompletion();
        game.pass(seat(2));
        PresidentBotView view = PresidentBotView.forSeat(game, seat(2));

        PresidentBotTuning never = new PresidentBotTuning(0, 0, 3, 2, 52, false, 0.0, false);

        assertThat(new HeuristicPresidentBot(never, ALWAYS_LOW).decide(view, new PresidentBotMemory(seat(2))),
                instanceOf(PresidentBotDecision.Keep.class));
    }

    @Test
    void anInTurnBotThatDeclinesTheCompletionPlaysOrPassesAsUsual() throws Exception {
        President game = cBoundHoldingTheCompletion();
        PresidentBotView view = PresidentBotView.forSeat(game, seat(2));
        PresidentBotTuning never = new PresidentBotTuning(0, 0, 3, 2, 52, false, 0.0, false);

        PresidentBotDecision decision = new HeuristicPresidentBot(never, ALWAYS_LOW)
                .decide(view, new PresidentBotMemory(seat(2)));

        // C holds 9D and 9C and is bound to a single 9: the lowest match.
        assertThat(decision, instanceOf(PresidentBotDecision.Play.class));
        assertThat(((PresidentBotDecision.Play) decision).cards().size(), is(1));
    }

    @Test
    void underTheBindingABotMatchesWhenItBreaksNoSetAndPassesWhenItCannot() throws Exception {
        President matching = aPresident().leader(0)
                .withHand("9S 9C 3C").withHand("9H 4C").withHand("9D 5C 7C").withHand("6C 7C").build();
        play(matching, 0, "9S");
        play(matching, 1, "9H");
        PresidentBotDecision match = decide(PresidentBotLevel.MEDIUM, ALWAYS_HIGH,
                PresidentBotView.forSeat(matching, seat(2)));

        assertThat(match, instanceOf(PresidentBotDecision.Play.class));
        assertThat(ranks(((PresidentBotDecision.Play) match).cards()), is("9"));

        President nothing = aPresident().leader(0)
                .withHand("9S 9C 3C").withHand("9H 4C").withHand("5C 7C KC").withHand("9D 6C").build();
        play(nothing, 0, "9S");
        play(nothing, 1, "9H");
        PresidentBotView view = PresidentBotView.forSeat(nothing, seat(2));

        assertThat(view.legalPlays().isEmpty(), is(true));
        assertThat(decide(PresidentBotLevel.HARD, ALWAYS_HIGH, view), instanceOf(PresidentBotDecision.Pass.class));
    }


}
