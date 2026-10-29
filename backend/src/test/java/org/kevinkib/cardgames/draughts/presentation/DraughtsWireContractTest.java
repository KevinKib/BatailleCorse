package org.kevinkib.cardgames.draughts.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;
import org.kevinkib.cardgames.draughts.presentation.dto.event.DraughtsEventType;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.support.WireContract;

import java.util.List;

import static org.kevinkib.cardgames.draughts.domain.DraughtsFactory.GAME_TYPE;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.BLACK;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.WHITE;

/** The JSON a draughts client receives, pinned in {@code contract/draughts/} (see {@link WireContract}). */
class DraughtsWireContractTest {

    private final DraughtsTable table = new DraughtsTable();

    private static DraughtsPosition layout(Colour toMove, String... specs) {
        char[] chars = ".".repeat(50).toCharArray();
        for (String spec : specs) {
            chars[Integer.parseInt(spec.substring(1)) - 1] = spec.charAt(0);
        }
        return DraughtsPosition.fromLayout(new String(chars), toMove);
    }

    @Test
    void theEventTypesArePinned() {
        WireContract.verifyEventTypes(GAME_TYPE, DraughtsEventType.values());
    }

    /** Short games from the moves of the other tests: a move, a capture, draw offers, the two ways to end. */
    @Test
    void aLobbyThenTheMovesOfAGame() throws Exception {
        WireContract contract = WireContract.of(GAME_TYPE);
        contract.lobby(table.sessions, table.messaging, GAME_TYPE, table.rest::joinGame, table.rest::getGame, "Bob");

        Draughts game = table.newGame();
        table.actions.move(game.getId(), WHITE, 32, 28, List.of());
        table.actions.offerDraw(game.getId(), BLACK);
        table.actions.declineDraw(game.getId(), WHITE);
        table.actions.offerDraw(game.getId(), WHITE);
        table.actions.acceptDraw(game.getId(), BLACK);

        Draughts capture = table.install(Draughts.fromPosition(GameId.generate(), WHITE,
                layout(Colour.WHITE, "w32", "b28", "b19", "b1")));
        table.actions.move(capture.getId(), WHITE, 32, 14, List.of(28, 19));

        Draughts end = table.install(Draughts.fromPosition(GameId.generate(), WHITE, layout(Colour.WHITE, "w27", "b21")));
        table.actions.move(end.getId(), WHITE, 27, 16, List.of(21));

        contract.messagesTo(table.messaging, 0)
                .rest("state", table.rest.getGame(end.getId().uuid().toString(), table.token(end, 0)).getBody())
                .verify("game");
    }

    @Test
    void theLifecycle() {
        WireContract contract = WireContract.of(GAME_TYPE);
        Draughts game = table.newGame();
        WireContract.playLifecycle(new SeatLifecycleBroadcaster<>(Draughts.class, table.broadcaster), game, 1);

        contract.messagesTo(table.messaging, 0).verify("lifecycle");
    }
}
