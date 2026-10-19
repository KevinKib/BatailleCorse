package org.kevinkib.cardgames.president.presentation.dto;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.event.ExchangeSeen;
import org.kevinkib.cardgames.president.domain.event.ExchangeViewClosed;
import org.kevinkib.cardgames.president.presentation.dto.event.PresidentEventData;
import org.kevinkib.cardgames.president.presentation.dto.event.PresidentEventType;
import org.kevinkib.cardgames.president.presentation.dto.event.PresidentExchangeClosedData;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/** The wire shape of the two events of the exchange view. */
class PresidentExchangeEventDataTest {

    @Test
    void anAcknowledgementNamesTheSeatAndWhetherThePlayBegins() {
        PresidentEventData opened = PresidentEventData.from(PresidentEventType.EXCHANGE_SEEN, 2,
                List.of(new ExchangeSeen(new PlayerId(2), true)));
        PresidentEventData waiting = PresidentEventData.from(PresidentEventType.EXCHANGE_SEEN, 1,
                List.of(new ExchangeSeen(new PlayerId(1), false)));

        assertThat(opened.action(), is("EXCHANGE_SEEN"));
        assertThat(opened.seat(), is(2));
        assertThat(opened.barrierOpened(), is(true));
        assertThat(waiting.barrierOpened(), is(false));
        assertThat(opened.cards(), is(nullValue()));
    }

    @Test
    void aCloseNamesTheRoundAndTheLateSeatsAsNumbers() {
        PresidentExchangeClosedData data = PresidentExchangeClosedData.from(
                new ExchangeViewClosed(2, List.of(new PlayerId(1), new PlayerId(3))));

        assertThat(data.round(), is(2));
        assertThat(data.late(), is(List.of(1, 3)));
    }
}
