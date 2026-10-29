package org.kevinkib.cardgames.threemenmorris.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.support.WireContract;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.event.ThreeMenMorrisEventType;

import static org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory.GAME_TYPE;

/**
 * The JSON a Three/Six Men's Morris client receives through the application layer, pinned in
 * {@code contract/three-men-morris/} (see {@link WireContract}). The byte-level characterisation of the DTO stays in
 * {@link ThreeMenMorrisWireContractTest}; this one feeds the frontend type check.
 */
class ThreeMenMorrisGameWireContractTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    private final ThreeMenMorrisTable table = new ThreeMenMorrisTable();

    private void place(ThreeMenMorris game, int[][] placements) throws Exception {
        for (int[] p : placements) {
            table.actions.place(game.getId(), new PlayerId(p[0]), p[1]);
        }
    }

    @Test
    void theEventTypesArePinned() {
        WireContract.verifyEventTypes(GAME_TYPE, ThreeMenMorrisEventType.values());
    }

    /** Placements, moves, draw offers, a Six Men's Morris mill and its removal, a line that ends the game. */
    @Test
    void aLobbyThenTheMovesOfAGame() throws Exception {
        WireContract contract = WireContract.of(GAME_TYPE);
        contract.lobby(table.sessions, table.messaging, GAME_TYPE, table.rest::joinGame, table.rest::getGame, "Bob");

        ThreeMenMorris game = table.newGame();
        place(game, new int[][]{{0, 0}, {1, 1}, {0, 2}, {1, 5}, {0, 7}, {1, 8}});
        table.actions.move(game.getId(), WHITE, 7, 6);
        table.actions.offerDraw(game.getId(), WHITE);
        table.actions.declineDraw(game.getId(), BLACK);
        table.actions.offerDraw(game.getId(), BLACK);
        table.actions.acceptDraw(game.getId(), WHITE);

        ThreeMenMorris six = table.install(new ThreeMenMorris(GameId.generate(), MorrisRules.SIX_MEN, WHITE));
        place(six, new int[][]{{0, 0}, {1, 8}, {0, 1}, {1, 10}, {0, 2}});
        table.actions.remove(six.getId(), WHITE, 8);

        ThreeMenMorris line = table.newGame();
        place(line, new int[][]{{0, 0}, {1, 3}, {0, 4}, {1, 1}, {0, 8}});

        contract.messagesTo(table.messaging, 0)
                .rest("state", table.rest.getGame(line.getId().uuid().toString(), table.token(line, 0)).getBody())
                .verify("game");
    }

    @Test
    void theLifecycle() {
        WireContract contract = WireContract.of(GAME_TYPE);
        ThreeMenMorris game = table.newGame();
        WireContract.playLifecycle(new SeatLifecycleBroadcaster<>(ThreeMenMorris.class, table.broadcaster), game, 1);

        contract.messagesTo(table.messaging, 0).verify("lifecycle");
    }
}
