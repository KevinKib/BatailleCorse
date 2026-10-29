package org.kevinkib.cardgames.bataillecorse.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseScenario;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.event.BatailleCorseEventType;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.support.SessionTable;
import org.kevinkib.cardgames.presentation.support.WireContract;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.Seat;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;

import java.util.List;

import static org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory.GAME_TYPE;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.FIVE;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.FOUR;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.JACK;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.TEN;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.THREE;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.TWO;

/**
 * The JSON a Bataille Corse client receives, pinned in {@code contract/bataille-corse/} (see {@link WireContract}).
 * The game messages go to the shared game topic, the lobby views to the seat topic.
 */
class BatailleCorseWireContractTest {

    private static final PlayerId SEAT_0 = new PlayerId(0);
    private static final PlayerId SEAT_1 = new PlayerId(1);

    private final SessionTable table = new SessionTable(new BatailleCorseFactory());
    private final ForfeitLog reasons = new InMemoryForfeitLog();
    private final BatailleCorseStateBroadcaster broadcaster = new BatailleCorseStateBroadcaster(table.messaging);
    private final BatailleCorseGameActions actions = new BatailleCorseGameActions(table.sessions, broadcaster);
    private final BatailleCorseRestController rest =
            new BatailleCorseRestController(table.sessions, reasons, new LobbyBroadcaster(table.messaging));

    @Test
    void theEventTypesArePinned() {
        WireContract.verifyEventTypes(GAME_TYPE, BatailleCorseEventType.values());
    }

    /** A send, a slap, a grab, then the lifecycle of a seat that disconnects and resigns. */
    @Test
    void aLobbyThenTheMovesOfAGame() throws Exception {
        WireContract contract = WireContract.of(GAME_TYPE);
        contract.lobby(table.sessions, table.messaging, GAME_TYPE, rest::joinGame, rest::getGame, "Bob");

        BatailleCorse slap = table.install(
                BatailleCorseScenario.suitedGame(List.of(TEN, THREE), List.of(FOUR, FIVE)), GAME_TYPE);
        actions.send(slap.getId(), SEAT_0);
        actions.slap(slap.getId(), SEAT_1);

        BatailleCorse grab = table.install(
                BatailleCorseScenario.suitedGame(List.of(JACK, THREE), List.of(FOUR, FIVE)), GAME_TYPE);
        actions.send(grab.getId(), SEAT_0);
        actions.send(grab.getId(), SEAT_1);
        actions.grab(grab.getId(), SEAT_0);

        BatailleCorseLifecycleBroadcaster lifecycle = new BatailleCorseLifecycleBroadcaster(broadcaster, reasons);
        lifecycle.disconnected(grab, SEAT_1, WireContract.DEADLINE_EPOCH_MS);
        lifecycle.reconnected(grab, SEAT_1);
        reasons.record(new Seat(grab.getId(), SEAT_1), ForfeitReason.RESIGNED);
        grab.forfeit(SEAT_1);
        lifecycle.forfeited(grab, SEAT_1, ForfeitReason.RESIGNED);

        contract.messages(table.messaging.toGame)
                .rest("state", rest.getGame(grab.getId().uuid().toString(), table.token(grab, 0)).getBody())
                .verify("game");
    }
}
