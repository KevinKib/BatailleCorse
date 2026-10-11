package org.kevinkib.cardgames.bataillecorse.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseScenario;
import org.kevinkib.cardgames.bataillecorse.domain.slaprules.CanSlapTens;
import org.kevinkib.cardgames.bataillecorse.domain.slaprules.SlapRules;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.bataillecorse.domain.bot.BatailleCorseBotStrategy.Move.NONE;
import static org.kevinkib.cardgames.bataillecorse.domain.bot.BatailleCorseBotStrategy.Move.SEND;
import static org.kevinkib.cardgames.bataillecorse.domain.bot.BatailleCorseBotStrategy.Move.SLAP;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.*;

class BatailleCorseBotStrategyTest {

    private static final PlayerId SEAT_0 = new PlayerId(0);
    private static final PlayerId SEAT_1 = new PlayerId(1);

    private final BatailleCorseBotStrategy strategy = new BatailleCorseBotStrategy();

    private BatailleCorse afterSends(List<FrenchRank> seat0, List<FrenchRank> seat1, int sends) {
        return BatailleCorseScenario.afterSends(seat0, seat1, sends);
    }

    @Test
    void sendsOnItsTurnWhenThePileIsOrdinary() {
        BatailleCorse game = afterSends(List.of(TWO, FOUR, SIX), List.of(THREE, FIVE, SEVEN), 1);

        assertThat(strategy.decide(game, SEAT_1), is(SEND));
    }

    @Test
    void doesNothingWhenItIsNotItsTurnAndThePileIsOrdinary() {
        BatailleCorse game = afterSends(List.of(TWO, FOUR, SIX), List.of(THREE, FIVE, SEVEN), 1);

        assertThat(strategy.decide(game, SEAT_0), is(NONE));
    }

    @Test
    void slapsATenOnTopEvenOutOfTurn() {
        BatailleCorse game = afterSends(List.of(TEN, FOUR), List.of(THREE, FIVE), 1);

        assertThat(strategy.decide(game, SEAT_0), is(SLAP));
        assertThat(strategy.decide(game, SEAT_1), is(SLAP));
    }

    @Test
    void slapsAPair() {
        BatailleCorse game = afterSends(List.of(FOUR, SIX), List.of(FOUR, FIVE), 2);

        assertThat(strategy.decide(game, SEAT_0), is(SLAP));
    }

    @Test
    void slapsASandwich() {
        BatailleCorse game = afterSends(List.of(FOUR, FOUR), List.of(FIVE, SEVEN), 3);

        assertThat(strategy.decide(game, SEAT_1), is(SLAP));
    }

    @Test
    void slapsASumOfTen() {
        BatailleCorse game = afterSends(List.of(FOUR, SIX), List.of(SIX, SEVEN), 2);

        assertThat(strategy.decide(game, SEAT_0), is(SLAP));
    }

    @Test
    void usesTheRulesInForceNotACopy() {
        // With only the "ten" rule, a pair is no longer slappable.
        BatailleCorse game = BatailleCorseScenario.game(
                List.of(FOUR, SIX), List.of(FOUR, FIVE), new SlapRules(List.of(new CanSlapTens())));
        BatailleCorseScenario.play(game, 2);

        assertThat(strategy.decide(game, SEAT_1), is(NONE));
    }

    @Test
    void doesNothingOnceTheGameIsOver() {
        BatailleCorse game = afterSends(List.of(TEN, FOUR), List.of(THREE, FIVE), 1);
        game.forfeit(SEAT_0);

        assertThat(strategy.decide(game, SEAT_1), is(NONE));
    }

    @Test
    void doesNothingWhenItCannotSendAndNothingIsSlappable() {
        // Seat 0 played a jack and seat 1 spent its chance: the pile is full, nobody may send.
        BatailleCorse game = afterSends(List.of(JACK, FOUR), List.of(THREE, FIVE), 2);

        assertThat(strategy.decide(game, SEAT_0), is(NONE));
        assertThat(strategy.decide(game, SEAT_1), is(NONE));
    }
}
