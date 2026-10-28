package org.kevinkib.cardgames.blackjack.presentation.dto.event;

import org.kevinkib.cardgames.blackjack.domain.event.BetPlaced;
import org.kevinkib.cardgames.blackjack.domain.event.BlackjackEvent;
import org.kevinkib.cardgames.blackjack.domain.event.Busted;
import org.kevinkib.cardgames.blackjack.domain.event.CardDrawn;
import org.kevinkib.cardgames.blackjack.domain.event.DealerBlackjack;
import org.kevinkib.cardgames.blackjack.domain.event.DealerPlayed;
import org.kevinkib.cardgames.blackjack.domain.event.Doubled;
import org.kevinkib.cardgames.blackjack.domain.event.Eliminated;
import org.kevinkib.cardgames.blackjack.domain.event.HandDealt;
import org.kevinkib.cardgames.blackjack.domain.event.HandSettled;
import org.kevinkib.cardgames.blackjack.domain.event.SplitHand;
import org.kevinkib.cardgames.blackjack.domain.event.Stood;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public description of one thing that happened at the table; only the fields of the event type are set, the others
 * are null. {@code card} is the card of a HIT or DOUBLE, or the up card of a DEAL; {@code cards} the flat cards the
 * hands of a SPLIT hold right after it (hand 0 then hand 1) or the dealer's drawn cards of a DEALER_PLAY;
 * {@code holeCard} the card a DEALER_BLACKJACK or DEALER_PLAY shows; {@code value} the hand total after a HIT or the
 * dealer's final total; {@code bets}, {@code nets} and {@code stacks} are by seat.
 */
public record BlackjackEventData(
        Integer seat,
        Integer handIndex,
        CardDto card,
        Integer value,
        List<CardDto> cards,
        CardDto holeCard,
        Integer handNumber,
        Integer button,
        Map<String, Integer> bets,
        Map<String, Integer> nets,
        Map<String, Integer> stacks,
        List<Integer> bestSeats) implements EventData {

    public static BlackjackEventData from(BlackjackEvent event) {
        if (event instanceof BetPlaced e) {
            return seat(e.seat().id());
        } else if (event instanceof HandDealt e) {
            return new BlackjackEventData(null, null, CardDto.from(e.upCard()), null, null, null, e.handNumber(),
                    e.button().id(), bySeat(e.bets()), null, null, null);
        } else if (event instanceof DealerBlackjack e) {
            return new BlackjackEventData(null, null, null, null, null, CardDto.from(e.holeCard()), null, null, null,
                    null, null, null);
        } else if (event instanceof CardDrawn e) {
            return new BlackjackEventData(e.seat().id(), e.handIndex(), CardDto.from(e.card()), e.value(), null, null,
                    null, null, null, null, null, null);
        } else if (event instanceof Stood e) {
            return new BlackjackEventData(e.seat().id(), e.handIndex(), null, null, null, null, null, null, null,
                    null, null, null);
        } else if (event instanceof Doubled e) {
            return new BlackjackEventData(e.seat().id(), e.handIndex(), CardDto.from(e.card()), null, null, null,
                    null, null, null, null, null, null);
        } else if (event instanceof SplitHand e) {
            return new BlackjackEventData(e.seat().id(), null, null, null,
                    e.cards().stream().map(CardDto::from).toList(), null, null, null, null, null, null, null);
        } else if (event instanceof Busted e) {
            return new BlackjackEventData(e.seat().id(), e.handIndex(), null, null, null, null, null, null, null,
                    null, null, null);
        } else if (event instanceof DealerPlayed e) {
            return new BlackjackEventData(null, null, null, e.value(),
                    e.drawn().stream().map(CardDto::from).toList(), CardDto.from(e.holeCard()), null, null, null,
                    null, null, null);
        } else if (event instanceof HandSettled e) {
            return new BlackjackEventData(null, null, null, null, null, null, e.handNumber(), null, null,
                    bySeat(e.nets()), bySeat(e.stacks()), e.bestSeats().stream().map(PlayerId::id).toList());
        } else if (event instanceof Eliminated e) {
            return new BlackjackEventData(e.seat().id(), null, null, null, null, null, e.handNumber(), null, null,
                    null, null, null);
        }
        return new BlackjackEventData(null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static BlackjackEventData seat(int seat) {
        return new BlackjackEventData(seat, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static Map<String, Integer> bySeat(Map<PlayerId, Integer> values) {
        Map<String, Integer> out = new LinkedHashMap<>();
        values.forEach((seat, value) -> out.put(String.valueOf(seat.id()), value));
        return out;
    }
}
