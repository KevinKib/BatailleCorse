package org.kevinkib.cardgames.ninetynine.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.event.Busted;
import org.kevinkib.cardgames.ninetynine.domain.event.Eliminated;
import org.kevinkib.cardgames.ninetynine.domain.event.Forfeited;
import org.kevinkib.cardgames.ninetynine.domain.event.GameFinished;
import org.kevinkib.cardgames.ninetynine.domain.event.NinetyNineResult;
import org.kevinkib.cardgames.ninetynine.domain.event.Played;
import org.kevinkib.cardgames.ninetynine.domain.event.RoundDealt;
import org.kevinkib.cardgames.ninetynine.domain.event.StockReshuffled;
import org.kevinkib.cardgames.ninetynine.presentation.dto.event.NinetyNineEventData;
import org.kevinkib.cardgames.ninetynine.presentation.dto.event.NinetyNineEventType;
import org.kevinkib.cardgames.presentation.dto.CardDto;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.ninetynine.presentation.NinetyNineTable.card;
import static org.kevinkib.cardgames.ninetynine.presentation.NinetyNineTable.cards;

/** What each domain event announces to the table: only its own public fields are set. */
class NinetyNineEventDataTest {

    private static final PlayerId S1 = new PlayerId(1);

    @Test
    void aPlayCarriesTheCardTheAceValueTheTotalAndTheReversal() {
        NinetyNineEventData data = NinetyNineEventData.from(new Played(S1, card("AS"), 11, 51, false));

        assertThat(NinetyNineEventType.of(new Played(S1, card("AS"), 11, 51, false))).isEqualTo(NinetyNineEventType.PLAY);
        assertThat(data).isEqualTo(new NinetyNineEventData(1, CardDto.from(card("AS")), 11, 51, false, null, null,
                null, null));
    }

    @Test
    void aReshuffleCarriesTheNewStockSizeOnly() {
        assertThat(NinetyNineEventType.of(new StockReshuffled(30))).isEqualTo(NinetyNineEventType.RESHUFFLE);
        assertThat(NinetyNineEventData.from(new StockReshuffled(30)))
                .isEqualTo(new NinetyNineEventData(null, null, null, null, null, null, null, null, 30));
    }

    @Test
    void aBustCarriesTheStuckHandAndTheLivesLeft() {
        Busted bust = new Busted(S1, 99, cards("7H 8H 2H"), 2);

        assertThat(NinetyNineEventType.of(bust)).isEqualTo(NinetyNineEventType.BUST);
        assertThat(NinetyNineEventData.from(bust)).isEqualTo(new NinetyNineEventData(1, null, null, null, null,
                cards("7H 8H 2H").stream().map(CardDto::from).toList(), 2, null, null));
    }

    @Test
    void anEliminationCarriesThePlace() {
        assertThat(NinetyNineEventType.of(new Eliminated(S1, 3))).isEqualTo(NinetyNineEventType.ELIMINATED);
        assertThat(NinetyNineEventData.from(new Eliminated(S1, 3)))
                .isEqualTo(new NinetyNineEventData(1, null, null, null, null, null, null, 3, null));
    }

    @Test
    void aRoundStartNamesTheSeatThatStarts() {
        assertThat(NinetyNineEventType.of(new RoundDealt(2, S1))).isEqualTo(NinetyNineEventType.ROUND_START);
        assertThat(NinetyNineEventData.from(new RoundDealt(2, S1)))
                .isEqualTo(new NinetyNineEventData(1, null, null, null, null, null, null, null, null));
    }

    @Test
    void theEndOfTheGameIsAGameOverWithNoField() {
        GameFinished finished = new GameFinished(new NinetyNineResult(Map.of(S1, 1), NinetyNineResult.Reason.QUICK));

        assertThat(NinetyNineEventType.of(finished)).isEqualTo(NinetyNineEventType.GAME_OVER);
        assertThat(NinetyNineEventData.from(finished))
                .isEqualTo(new NinetyNineEventData(null, null, null, null, null, null, null, null, null));
    }

    @Test
    void aForfeitIsAnnouncedByTheLifecycleBroadcasterNotByTheTableEvents() {
        assertThat(NinetyNineEventType.of(new Forfeited(S1))).isNull();
        assertThat(List.of(NinetyNineEventType.values())).extracting(Enum::name).containsExactly(
                "START", "ROUND_START", "PLAY", "RESHUFFLE", "BUST", "ELIMINATED", "GAME_OVER");
    }
}
