package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisVariant;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;

import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class RandomBotTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    @ParameterizedTest
    @EnumSource(value = MorrisVariant.class, names = {"TAPATAN", "ACHI"})
    void everyDecisionOfARandomGameIsAcceptedByTheAggregate(MorrisVariant variant) throws Exception {
        MorrisBot bot = new RandomBot(new Random(7));
        for (int game = 0; game < 200; game++) {
            ThreeMenMorris aggregate = new ThreeMenMorris(GameId.generate(), variant.rules(), new PlayerId(game % 2));
            int plies = 0;
            while (!aggregate.isFinished() && plies++ < 300) {
                PlayerId seat = aggregate.currentSeat();
                MorrisDecision decision = bot.decide(aggregate.position(), seat, false);
                assertThat(decision, notNullValue());
                if (decision instanceof MorrisDecision.Place place) {
                    aggregate.place(seat, place.point());
                } else if (decision instanceof MorrisDecision.Step step) {
                    aggregate.move(seat, step.from(), step.to());
                } else {
                    throw new AssertionError("A bot with no offer to answer must move: " + decision);
                }
            }
            assertThat("the game ended (a win or a repetition)", aggregate.isFinished(), is(true));
        }
    }

    @Test
    void withTheFirstGeneratorItPlaysTheFirstLegalDecision() {
        MorrisBot bot = new RandomBot(Positions.FIRST);

        assertThat(bot.decide(Positions.tapatan(".........", 3, 3, 0), WHITE, false),
                is(new MorrisDecision.Place(0)));
        assertThat(bot.decide(Positions.tapatan("WW.BBW..B", 0, 0, 0), WHITE, false),
                is(new MorrisDecision.Step(1, 2)));
    }

    @Test
    void itAnswersADrawOfferWithTheSolverRuleAtEveryLevel() {
        MorrisPosition winning = Positions.tapatan("WW.BBW..B", 0, 0, 0);
        MorrisBot bot = new RandomBot(Positions.FIRST);

        assertThat(bot.decide(winning, WHITE, true), is(new MorrisDecision.DeclineDraw()));
        assertThat(bot.decide(winning, BLACK, true), is(new MorrisDecision.AcceptDraw()));
    }
}
