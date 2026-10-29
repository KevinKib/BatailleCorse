package org.kevinkib.cardgames.blackjack.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.Blackjack;
import org.kevinkib.cardgames.blackjack.presentation.api.BlackjackBetPayload;
import org.kevinkib.cardgames.blackjack.presentation.dto.event.BlackjackEventType;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.support.WireContract;

import java.util.Map;

import static org.kevinkib.cardgames.blackjack.domain.BlackjackFactory.GAME_TYPE;

/** The JSON a Blackjack client receives, pinned in {@code contract/blackjack/} (see {@link WireContract}). */
class BlackjackWireContractTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);
    /** Seat 0 holds 10S 7C, seat 1 9H 8C, the dealer 6D over 5H; the next cards are 10D 3H. */
    private static final String HAND_1 = "10S 9H 6D 7C 8C 5H 10D 3H";

    private final BlackjackTable table = new BlackjackTable();

    @Test
    void theEventTypesArePinned() {
        WireContract.verifyEventTypes(GAME_TYPE, BlackjackEventType.values());
    }

    /** Short hands from the moves of the other tests: a refused bet, bets, a bust, the dealer, a split, a double and the end. */
    @Test
    void aLobbyThenTheMovesOfAGame() throws Exception {
        WireContract contract = WireContract.of(GAME_TYPE);
        contract.lobby(table.sessions, table.messaging, GAME_TYPE, table.rest::joinGame, table.rest::getGame, "Bob");

        Blackjack hand = table.start(2, HAND_1);
        table.ws.bet(new BlackjackBetPayload(hand.getId().uuid().toString(), table.token(hand, 0), 0));
        table.actions.bet(hand.getId(), S0, 10);
        table.actions.bet(hand.getId(), S1, 20);
        table.actions.hit(hand.getId(), S0);
        table.actions.stand(hand.getId(), S1);

        Blackjack split = table.start(1, "8S 6D 8C 5H 3H 10D 4S");
        table.actions.bet(split.getId(), S0, 10);
        table.actions.split(split.getId(), S0);

        Blackjack doubled = table.start(1, "5S 6D 6C 5H 10D");
        table.actions.bet(doubled.getId(), S0, 10);
        table.actions.doubleDown(doubled.getId(), S0);

        Blackjack end = table.start(2, GameOptions.of(Map.of("maxBet", "100")), HAND_1);
        table.actions.bet(end.getId(), S0, 100);
        table.actions.bet(end.getId(), S1, 10);
        table.actions.stand(end.getId(), S0);
        table.actions.stand(end.getId(), S1);

        contract.messagesTo(table.messaging, 0)
                .rest("state", table.rest.getGame(end.getId().uuid().toString(), table.token(end, 0)).getBody())
                .verify("game");
    }

    @Test
    void theLifecycle() {
        WireContract contract = WireContract.of(GAME_TYPE);
        Blackjack game = table.start(2, HAND_1);
        WireContract.playLifecycle(new BlackjackLifecycleBroadcaster(table.broadcaster, table.actions), game, 1);

        contract.messagesTo(table.messaging, 0).verify("lifecycle");
    }
}
