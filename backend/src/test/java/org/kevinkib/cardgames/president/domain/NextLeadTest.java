package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.rules.ExchangeAmount;
import org.kevinkib.cardgames.president.domain.rules.NextLead;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class NextLeadTest {

    /** Round 1 ends 2, 0, 4, 1, 3: seat 2 is the President and seat 3 the Scum. */
    @ParameterizedTest(name = "{0} leads round 2")
    @CsvSource({"SCUM, 3", "PRESIDENT, 2"})
    void theConfiguredTitleLeadsRoundsTwoAndLater(NextLead nextLead, int expectedLeader) throws Exception {
        PresidentOptions options = PresidentOptions.defaults().withExchange(ExchangeAmount.NONE)
                .withNextLead(nextLead);
        President game = OrderedRounds.playedRoundOne(GameId.generate(), new RandomDeckSource(), options,
                2, 0, 4, 1, 3);

        for (PlayerId seat : game.getPlayerIds()) {
            game.ready(seat);
        }

        assertThat(game.round(), is(2));
        assertThat(game.currentSeat(), is(new PlayerId(expectedLeader)));
        assertThat(game.trick().leader(), is(new PlayerId(expectedLeader)));
    }

    @ParameterizedTest(name = "{0} leads round 2 after the exchange")
    @CsvSource({"SCUM, 3", "PRESIDENT, 2"})
    void theLeaderIsTheSameWhenThereIsAnExchange(NextLead nextLead, int expectedLeader) throws Exception {
        PresidentOptions options = PresidentOptions.defaults().withNextLead(nextLead);
        President game = OrderedRounds.playedRoundOne(GameId.generate(), new RandomDeckSource(), options,
                2, 0, 4, 1, 3);
        for (PlayerId seat : game.getPlayerIds()) {
            game.ready(seat);
        }

        while (!game.pendingReturns().isEmpty()) {
            var pending = game.pendingReturns().get(0);
            game.giveBack(pending.high(), game.hand(pending.high()).subList(0, pending.count()));
        }

        assertThat(game.currentSeat(), is(new PlayerId(expectedLeader)));
    }
}
