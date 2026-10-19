package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cardgames.president.domain.round.Phase;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;

/** The shared contract of the exchange view: the names exist, and nothing enters the view yet (chunk 0). */
class ExchangeViewContractTest {

    private static PlayerId p(int id) {
        return new PlayerId(id);
    }

    @Test
    void theViewPhaseSitsBetweenTheExchangeAndThePlay() {
        assertThat(Arrays.asList(Phase.values()),
                is(List.of(Phase.EXCHANGE, Phase.EXCHANGE_VIEW, Phase.PLAYING, Phase.ROUND_OVER, Phase.FINISHED)));
        assertThat(Action.valueOf("SEE_EXCHANGE"), is(Action.SEE_EXCHANGE));
    }

    @Test
    void anAcknowledgementOrACloseChangesNothingYet() throws Exception {
        President game = aPresident().withHand("4C 9C").withHand("5C 6C").withHand("7C 8C").leader(0).build();
        int version = game.version();

        assertThat(game.seeExchange(p(0), 1), is(empty()));
        assertThat(game.closeExchangeView(1), is(empty()));

        assertThat(game.exchangeSeen(), is(empty()));
        assertThat(game.version(), is(version));
        assertThat(game.phase(), is(Phase.PLAYING));
    }
}
