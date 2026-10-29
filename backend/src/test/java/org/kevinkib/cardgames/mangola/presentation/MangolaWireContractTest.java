package org.kevinkib.cardgames.mangola.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaGeometry;
import org.kevinkib.cardgames.mangola.presentation.dto.event.MangolaEventType;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.support.WireContract;

import static org.kevinkib.cardgames.mangola.domain.MangolaFactory.GAME_TYPE;
import static org.kevinkib.cardgames.mangola.presentation.MangolaTable.SEAT_0;
import static org.kevinkib.cardgames.mangola.presentation.MangolaTable.SEAT_1;

/** The JSON a Mangola client receives, pinned in {@code contract/mangola/} (see {@link WireContract}). */
class MangolaWireContractTest {

    private final MangolaTable table = new MangolaTable();

    @Test
    void theEventTypesArePinned() {
        WireContract.verifyEventTypes(GAME_TYPE, MangolaEventType.values());
    }

    /** The placement (arrange, confirm, edit), the start of play, a sowing that captures and one that ends. */
    @Test
    void aLobbyThenTheMovesOfAGame() throws Exception {
        WireContract contract = WireContract.of(GAME_TYPE);
        contract.lobby(table.sessions, table.messaging, GAME_TYPE, table.rest::joinGame, table.rest::getGame, "Bob");

        Mangola placement = table.placementGame();
        table.actions.arrange(placement.getId(), SEAT_0, MangolaTable.twoPerPit());
        table.actions.confirmLayout(placement.getId(), SEAT_0);
        table.actions.editLayout(placement.getId(), SEAT_0);
        table.actions.arrange(placement.getId(), SEAT_1, MangolaTable.allOnFirst());
        table.actions.confirmLayout(placement.getId(), SEAT_0);
        table.actions.confirmLayout(placement.getId(), SEAT_1);

        Mangola capture = table.playGame(MangolaTable.board("a2:2 c2:1 c3:2 c4:3 h4:5"));
        table.actions.sow(capture.getId(), SEAT_0, MangolaGeometry.pit("a2"));

        Mangola end = table.playGame(MangolaTable.board("a2:2 c2:1 c3:2 c4:3"));
        table.actions.sow(end.getId(), SEAT_0, MangolaGeometry.pit("a2"));

        contract.messagesTo(table.messaging, 0)
                .rest("state", table.rest.getGame(end.getId().uuid().toString(), table.token(end, 0)).getBody())
                .verify("game");
    }

    @Test
    void theLifecycle() {
        WireContract contract = WireContract.of(GAME_TYPE);
        Mangola game = table.placementGame();
        WireContract.playLifecycle(new SeatLifecycleBroadcaster<>(Mangola.class, table.broadcaster), game, 1);

        contract.messagesTo(table.messaging, 0).verify("lifecycle");
    }
}
