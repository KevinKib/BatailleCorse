package org.kevinkib.cardgames.blackjack.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.event.BetPlaced;
import org.kevinkib.cardgames.blackjack.domain.event.BlackjackResult;
import org.kevinkib.cardgames.blackjack.domain.event.Busted;
import org.kevinkib.cardgames.blackjack.domain.event.CardDrawn;
import org.kevinkib.cardgames.blackjack.domain.event.DealerBlackjack;
import org.kevinkib.cardgames.blackjack.domain.event.DealerPlayed;
import org.kevinkib.cardgames.blackjack.domain.event.Doubled;
import org.kevinkib.cardgames.blackjack.domain.event.Eliminated;
import org.kevinkib.cardgames.blackjack.domain.event.Forfeited;
import org.kevinkib.cardgames.blackjack.domain.event.GameFinished;
import org.kevinkib.cardgames.blackjack.domain.event.HandDealt;
import org.kevinkib.cardgames.blackjack.domain.event.HandSettled;
import org.kevinkib.cardgames.blackjack.domain.event.SplitHand;
import org.kevinkib.cardgames.blackjack.domain.event.Stood;
import org.kevinkib.cardgames.blackjack.presentation.dto.event.BlackjackEventData;
import org.kevinkib.cardgames.blackjack.presentation.dto.event.BlackjackEventType;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.dto.CardDto;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.blackjack.presentation.BlackjackTable.card;
import static org.kevinkib.cardgames.blackjack.presentation.BlackjackTable.cards;

/** Each domain event maps to one type and to the public fields of that type; every other field is null. */
class BlackjackEventDataTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);

    private static CardDto dto(String code) {
        return CardDto.from(card(code));
    }

    private static List<CardDto> dtos(String codes) {
        return cards(codes).stream().map(CardDto::from).toList();
    }

    @Test
    void everyEventHasItsTypeAndAForfeitHasNone() {
        assertThat(BlackjackEventType.of(new BetPlaced(S0))).isEqualTo(BlackjackEventType.BET);
        assertThat(BlackjackEventType.of(new HandDealt(1, S0, Map.of(S0, 10), card("6D"), Map.of())))
                .isEqualTo(BlackjackEventType.DEAL);
        assertThat(BlackjackEventType.of(new DealerBlackjack(card("KD")))).isEqualTo(BlackjackEventType.DEALER_BLACKJACK);
        assertThat(BlackjackEventType.of(new CardDrawn(S0, 0, card("2C"), 12))).isEqualTo(BlackjackEventType.HIT);
        assertThat(BlackjackEventType.of(new Stood(S0, 0))).isEqualTo(BlackjackEventType.STAND);
        assertThat(BlackjackEventType.of(new Doubled(S0, 0, card("2C")))).isEqualTo(BlackjackEventType.DOUBLE);
        assertThat(BlackjackEventType.of(new SplitHand(S0, cards("8C 3H 8D")))).isEqualTo(BlackjackEventType.SPLIT);
        assertThat(BlackjackEventType.of(new Busted(S0, 0))).isEqualTo(BlackjackEventType.BUST);
        assertThat(BlackjackEventType.of(new DealerPlayed(card("5H"), List.of(), 17)))
                .isEqualTo(BlackjackEventType.DEALER_PLAY);
        assertThat(BlackjackEventType.of(new HandSettled(1, Map.of(), Map.of(), List.of())))
                .isEqualTo(BlackjackEventType.SETTLE);
        assertThat(BlackjackEventType.of(new Eliminated(S0, 1))).isEqualTo(BlackjackEventType.ELIMINATED);
        assertThat(BlackjackEventType.of(new GameFinished(new BlackjackResult(Map.of(), Map.of(),
                BlackjackResult.Reason.BROKE)))).isEqualTo(BlackjackEventType.GAME_OVER);
        assertThat(BlackjackEventType.of(new Forfeited(S0))).isNull();
    }

    @Test
    void aDealCarriesTheButtonTheBetsAndTheUpCardByHandNumber() {
        BlackjackEventData data = BlackjackEventData.from(
                new HandDealt(3, S1, Map.of(S0, 10, S1, 20), card("6D"), Map.of(S0, cards("10S 7C"))));

        assertThat(data.handNumber()).isEqualTo(3);
        assertThat(data.button()).isEqualTo(1);
        assertThat(data.bets()).containsEntry("0", 10).containsEntry("1", 20);
        assertThat(data.card()).isEqualTo(dto("6D"));
        assertThat(data.seat()).isNull();
        assertThat(data.holeCard()).isNull();
    }

    @Test
    void aHitCarriesTheSeatTheHandTheCardAndTheNewTotal() {
        BlackjackEventData data = BlackjackEventData.from(new CardDrawn(S1, 1, card("4S"), 15));

        assertThat(data.seat()).isEqualTo(1);
        assertThat(data.handIndex()).isEqualTo(1);
        assertThat(data.card()).isEqualTo(dto("4S"));
        assertThat(data.value()).isEqualTo(15);
        assertThat(data.cards()).isNull();
    }

    @Test
    void aSplitCarriesTheFlatCardsHeldRightAfterTheSplit() {
        BlackjackEventData data = BlackjackEventData.from(new SplitHand(S0, cards("8C 3H 8D")));

        assertThat(data.seat()).isZero();
        assertThat(data.cards()).isEqualTo(dtos("8C 3H 8D"));
    }

    @Test
    void theDealerPlayShowsTheHoleCardTheDrawnCardsAndTheFinalTotal() {
        BlackjackEventData data = BlackjackEventData.from(new DealerPlayed(card("5H"), cards("10D"), 21));

        assertThat(data.holeCard()).isEqualTo(dto("5H"));
        assertThat(data.cards()).isEqualTo(dtos("10D"));
        assertThat(data.value()).isEqualTo(21);
        assertThat(BlackjackEventData.from(new DealerBlackjack(card("KD"))).holeCard()).isEqualTo(dto("KD"));
    }

    @Test
    void aSettlementCarriesNetsStacksAndTheBestSeats() {
        BlackjackEventData data = BlackjackEventData.from(
                new HandSettled(2, Map.of(S0, -10, S1, 15), Map.of(S0, 90, S1, 115), List.of(S1)));

        assertThat(data.handNumber()).isEqualTo(2);
        assertThat(data.nets()).containsEntry("0", -10).containsEntry("1", 15);
        assertThat(data.stacks()).containsEntry("0", 90).containsEntry("1", 115);
        assertThat(data.bestSeats()).containsExactly(1);
    }

    @Test
    void anEliminationCarriesTheSeatAndTheHand() {
        BlackjackEventData data = BlackjackEventData.from(new Eliminated(S1, 4));

        assertThat(data.seat()).isEqualTo(1);
        assertThat(data.handNumber()).isEqualTo(4);
    }
}
