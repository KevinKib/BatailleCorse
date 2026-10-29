package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.presentation.api.CreateLobbyPayload;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.LobbyOptionsPayload;
import org.kevinkib.cardgames.presentation.api.LobbySeatPayload;
import org.kevinkib.cardgames.presentation.dto.LobbyCreatedDto;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.presentation.support.SessionTable;
import org.kevinkib.cardgames.presentation.support.WireContract;
import org.kevinkib.cardgames.sessionmanagement.presence.application.GameLifecycleBroadcasters;
import org.kevinkib.cardgames.sessionmanagement.presence.application.PresenceService;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryConnectionRegistry;
import org.kevinkib.cardgames.sessionmanagement.presence.infrastructure.InMemoryForfeitLog;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/**
 * The JSON of the shared session layer, pinned in {@code contract/session/} (see {@link WireContract}): room
 * creation, the lobby views of the lobby commands, the removal of a seat, the public game list and the seat list.
 */
class SessionWireContractTest {

    private final SessionTable table = new SessionTable(new BullshitFactory(), new BatailleCorseFactory());
    private final PresenceService presence = new PresenceService(table.sessions, new InMemoryConnectionRegistry(),
            (deadline, task) -> () -> { }, Clock.systemUTC(), new InMemoryForfeitLog(),
            new GameLifecycleBroadcasters(List.of()));
    private final LobbyController lobby = new LobbyController(table.sessions, table.messaging, presence);

    @Test
    void theLifecycleEventTypesArePinned() {
        WireContract.verifyEventTypes("session", LifecycleEventType.values());
    }

    @Test
    void aLobbyFromItsCreationToTheRemovalOfASeat() {
        WireContract contract = WireContract.of("session");
        LobbyCreatedDto created = new LobbyRestController(table.sessions)
                .create(new CreateLobbyPayload("bullshit", "Alice", false)).getBody();
        contract.rest("created", created);
        String id = created.gameId();
        String host = created.token();
        table.sessions.joinRoom(new GameId(id), "Bob");

        lobby.addBot(new GameActionPayload(id, host));
        lobby.setOptions(new LobbyOptionsPayload(id, host, Map.of("deckSize", "52")));
        lobby.removeSeat(new LobbySeatPayload(id, host, 1));

        contract.messagesTo(table.messaging, 0)
                .messages(table.messaging.detachedPayloads)
                .rest("lobbies", new PublicLobbyRestController(table.sessions).list("bullshit").getBody())
                .rest("session", new SessionRestController(table.sessions).getSession(id).getBody())
                .verify("lobby");
    }
}
