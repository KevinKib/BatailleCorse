package org.kevinkib.cardgames.blackjack.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.AlreadyBetException;
import org.kevinkib.cardgames.blackjack.domain.Blackjack;
import org.kevinkib.cardgames.blackjack.domain.InvalidBetException;
import org.kevinkib.cardgames.blackjack.domain.NotPlayersTurnException;
import org.kevinkib.cardgames.blackjack.domain.WrongPhaseException;
import org.kevinkib.cardgames.blackjack.presentation.dto.BlackjackDto;
import org.kevinkib.cardgames.blackjack.presentation.dto.event.BlackjackEventData;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.CardDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kevinkib.cardgames.blackjack.presentation.BlackjackTable.card;

class BlackjackGameActionsTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);

    /** Seat 0 holds 10S 7C, seat 1 9H 8C, the dealer 6D over 5H; the next cards are 10D 3H. */
    private static final String HAND_1 = "10S 9H 6D 7C 8C 5H 10D 3H";

    private final BlackjackTable table = new BlackjackTable();

    private List<String> eventTypes() {
        return table.messaging.payloads.stream().map(Response::getEventType).toList();
    }

    @Test
    void aBetBroadcastsOneBetEventToEverySeatWithItsOwnState() throws Exception {
        Blackjack game = table.start(2, HAND_1);

        table.actions.bet(game.getId(), S0, 10);

        assertThat(table.messaging.seats).containsExactly(S0, S1);
        Response first = table.messaging.payloads.get(0);
        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getEventType()).isEqualTo("BET");
        assertThat(((BlackjackEventData) first.getEventData()).seat()).isZero();
        BlackjackDto mine = (BlackjackDto) first.getState();
        assertThat(mine.players().get(0).bet()).isEqualTo(10);
        BlackjackDto theirs = (BlackjackDto) table.messaging.payloads.get(1).getState();
        assertThat(theirs.players().get(0).bet()).isNull();
        assertThat(theirs.players().get(0).betPlaced()).isTrue();
    }

    @Test
    void theLastBetAnnouncesTheBetThenTheDealWithTheStateAfterTheWholeCommand() throws Exception {
        Blackjack game = table.start(2, HAND_1);
        table.actions.bet(game.getId(), S0, 10);
        table.messaging.clear();

        table.actions.bet(game.getId(), S1, 20);

        assertThat(eventTypes()).containsExactly("BET", "BET", "DEAL", "DEAL");
        BlackjackEventData deal = (BlackjackEventData) table.messaging.payloads.get(2).getEventData();
        assertThat(deal.handNumber()).isEqualTo(1);
        assertThat(deal.button()).isZero();
        assertThat(deal.bets()).containsEntry("0", 10).containsEntry("1", 20);
        assertThat(((BlackjackDto) table.messaging.payloads.get(0).getState()).phase()).isEqualTo("PLAYING");
    }

    @Test
    void aHitThatBustsAnnouncesTheHitThenTheBust() throws Exception {
        Blackjack game = table.start(2, HAND_1);
        table.actions.bet(game.getId(), S0, 10);
        table.actions.bet(game.getId(), S1, 20);
        table.messaging.clear();

        table.actions.hit(game.getId(), S0);

        assertThat(eventTypes()).containsExactly("HIT", "HIT", "BUST", "BUST");
        Response bustResponse = table.messaging.payloads.get(2);
        BlackjackEventData bust = (BlackjackEventData) bustResponse.getEventData();
        assertThat(bust.seat()).isZero();
        assertThat(bust.handIndex()).isZero();
    }

    @Test
    void aHitThatStaysInTheGameIsAHitEvent() throws Exception {
        Blackjack game = table.start(2, "2S 9H 6D 3C 8C 5H 4D 3H");
        table.actions.bet(game.getId(), S0, 10);
        table.actions.bet(game.getId(), S1, 20);
        table.messaging.clear();

        table.actions.hit(game.getId(), S0);

        assertThat(eventTypes()).containsExactly("HIT", "HIT");
        BlackjackEventData data = (BlackjackEventData) table.messaging.payloads.get(0).getEventData();
        assertThat(data.card()).isEqualTo(CardDto.from(card("4D")));
        assertThat(data.value()).isEqualTo(9);
    }

    @Test
    void theLastStandAnnouncesStandDealerPlaySettleForEverySeat() throws Exception {
        Blackjack game = table.start(2, HAND_1);
        table.actions.bet(game.getId(), S0, 10);
        table.actions.bet(game.getId(), S1, 20);
        table.actions.stand(game.getId(), S0);
        table.messaging.clear();

        table.actions.stand(game.getId(), S1);

        assertThat(eventTypes()).containsExactly("STAND", "STAND", "DEALER_PLAY", "DEALER_PLAY", "SETTLE", "SETTLE");
        BlackjackEventData played = (BlackjackEventData) table.messaging.payloads.get(2).getEventData();
        assertThat(played.holeCard()).isEqualTo(CardDto.from(card("5H")));
        assertThat(played.cards()).containsExactly(CardDto.from(card("10D")));
        assertThat(played.value()).isEqualTo(21);
        BlackjackEventData settled = (BlackjackEventData) table.messaging.payloads.get(4).getEventData();
        assertThat(settled.nets()).containsEntry("0", -10).containsEntry("1", -20);
        assertThat(settled.stacks()).containsEntry("0", 90).containsEntry("1", 80);
        assertThat(((BlackjackDto) table.messaging.payloads.get(4).getState()).phase()).isEqualTo("BETTING");
    }

    @Test
    void aSplitAnnouncesTheFlatCardsOfBothHands() throws Exception {
        Blackjack game = table.start(1, "8S 6D 8C 5H 3H 10D 4S");
        table.actions.bet(game.getId(), S0, 10);
        table.messaging.clear();

        table.actions.split(game.getId(), S0);

        assertThat(eventTypes()).containsExactly("SPLIT");
        BlackjackEventData data = (BlackjackEventData) table.messaging.payloads.get(0).getEventData();
        assertThat(data.cards()).containsExactly(CardDto.from(card("8S")), CardDto.from(card("3H")),
                CardDto.from(card("8C")));
    }

    @Test
    void aDoubleAnnouncesTheDrawnCard() throws Exception {
        Blackjack game = table.start(1, "5S 6D 6C 5H 10D");
        table.actions.bet(game.getId(), S0, 10);
        table.messaging.clear();

        table.actions.doubleDown(game.getId(), S0);

        assertThat(eventTypes().get(0)).isEqualTo("DOUBLE");
        assertThat(((BlackjackEventData) table.messaging.payloads.get(0).getEventData()).card())
                .isEqualTo(CardDto.from(card("10D")));
    }

    @Test
    void anEliminationAndTheEndOfTheGameAreAnnouncedAfterTheSettlement() throws Exception {
        Blackjack game = table.start(2, org.kevinkib.cardgames.game.GameOptions.of(java.util.Map.of("maxBet", "100")),
                "10S 9H 6D 7C 8C 5H 10D 3H");
        table.actions.bet(game.getId(), S0, 100);
        table.actions.bet(game.getId(), S1, 10);
        table.actions.stand(game.getId(), S0);
        table.messaging.clear();

        table.actions.stand(game.getId(), S1);

        assertThat(eventTypes()).containsSubsequence("SETTLE", "ELIMINATED", "GAME_OVER");
        assertThat(((BlackjackDto) table.messaging.payloads.get(table.messaging.payloads.size() - 1).getState()).phase())
                .isEqualTo("FINISHED");
    }

    @Test
    void aRejectedCommandBroadcastsNothingAndPropagates() throws Exception {
        Blackjack game = table.start(2, HAND_1);
        int version = game.version();

        assertThatThrownBy(() -> table.actions.hit(game.getId(), S0)).isInstanceOf(WrongPhaseException.class);
        assertThatThrownBy(() -> table.actions.bet(game.getId(), S0, 0)).isInstanceOf(InvalidBetException.class);
        assertThatThrownBy(() -> table.actions.bet(game.getId(), S0, 51)).isInstanceOf(InvalidBetException.class);
        table.actions.bet(game.getId(), S0, 10);
        table.messaging.clear();
        assertThatThrownBy(() -> table.actions.bet(game.getId(), S0, 10)).isInstanceOf(AlreadyBetException.class);
        table.actions.bet(game.getId(), S1, 10);
        table.messaging.clear();
        assertThatThrownBy(() -> table.actions.stand(game.getId(), S1)).isInstanceOf(NotPlayersTurnException.class);

        assertThat(table.messaging.payloads).isEmpty();
        assertThat(game.version()).isGreaterThan(version);
    }

    @Test
    void aForfeitAftermathIsAnnouncedAfterTheGenericForfeit() {
        Blackjack game = table.start(2, HAND_1);
        game.forfeit(S1);

        table.actions.announceForfeitAftermath(game, "Player 1 forfeited.");

        assertThat(eventTypes().stream().distinct().toList()).containsExactly("GAME_OVER");
    }
}
