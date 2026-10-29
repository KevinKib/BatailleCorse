package org.kevinkib.cardgames.kilodemerde.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.event.KiloDeMerdeEventType;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.support.WireContract;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;

import static org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeFactory.GAME_TYPE;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.ALL_SETS;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.PILE;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.card;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.cards;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.hands;

/** The JSON a Kilo de merde client receives, pinned in {@code contract/kilo-de-merde/} (see {@link WireContract}). */
class KiloDeMerdeWireContractTest {

    private final KiloDeMerdeTable table = new KiloDeMerdeTable();

    @Test
    void theEventTypesArePinned() {
        WireContract.verifyEventTypes(GAME_TYPE, KiloDeMerdeEventType.values());
    }

    @Test
    void aGameFromTheLobbyToTheForfeit() throws Exception {
        WireContract contract = WireContract.of(GAME_TYPE);
        RoomCreated room = contract.lobby(table.sessions, table.messaging, GAME_TYPE, table.rest::joinGame,
                table.rest::getGame, "Bob", "Cleo");
        GameId id = new GameId(room.gameId());
        table.decks.fixDeal(id, hands(ALL_SETS), cards(PILE));

        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));
        KiloDeMerde game = (KiloDeMerde) table.sessions.findGame(id).orElseThrow();
        for (int seat = 0; seat < 3; seat++) {
            table.actions.ready(id, new PlayerId(seat));
        }
        table.actions.choosePass(id, new PlayerId(0), card("KS"));
        table.actions.slap(id, new PlayerId(1));
        table.actions.slap(id, new PlayerId(2));
        table.actions.ready(id, new PlayerId(0));
        WireContract.playLifecycle(new KiloDeMerdeLifecycleBroadcaster(table.broadcaster, table.actions), game, 1);

        contract.messagesTo(table.messaging, 0)
                .rest("state", table.rest.getGame(room.gameId(), room.hostToken()).getBody())
                .verify("game");
    }
}
