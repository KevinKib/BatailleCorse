package org.kevinkib.cardgames.ninetynine.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.presentation.dto.event.NinetyNineEventType;
import org.kevinkib.cardgames.presentation.support.WireContract;

import java.util.Map;

import static org.kevinkib.cardgames.ninetynine.domain.NinetyNineFactory.GAME_TYPE;
import static org.kevinkib.cardgames.ninetynine.presentation.NinetyNineTable.card;

/** The JSON a 99 client receives, pinned in {@code contract/ninety-nine/} (see {@link WireContract}). */
class NinetyNineWireContractTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);

    private final NinetyNineTable table = new NinetyNineTable();

    @Test
    void theEventTypesArePinned() {
        WireContract.verifyEventTypes(GAME_TYPE, NinetyNineEventType.values());
    }

    @Test
    void aLobbyThenAGamePlayedToItsEnd() throws Exception {
        WireContract contract = WireContract.of(GAME_TYPE);
        contract.lobby(table.sessions, table.messaging, GAME_TYPE, table.rest::joinGame, table.rest::getGame, "Bob");

        NinetyNine game = table.start(2, GameOptions.of(Map.of("lives", "2")),
                "KS 7H 9S 8H 4S 2H 5D 6C 7C 8C", "KD 9D 5D 8D 6D 7D 2D 3D 4C 5C");
        table.actions.play(game.getId(), S0, card("KS"), null);
        table.actions.play(game.getId(), S1, card("7H"), null);
        table.actions.nextRound(game.getId(), game.version());
        table.actions.play(game.getId(), S1, card("9D"), null);
        table.actions.play(game.getId(), S0, card("KD"), null);
        table.actions.play(game.getId(), S1, card("8D"), null);

        contract.messagesTo(table.messaging, 0)
                .rest("state", table.rest.getGame(game.getId().uuid().toString(), table.token(game, 0)).getBody())
                .verify("game");
    }

    @Test
    void anAceAFourAndTheLifecycle() throws Exception {
        WireContract contract = WireContract.of(GAME_TYPE);
        NinetyNine game = table.start(3, "AS 7H JC 4S 8H QC 5S 2H 3C 5D 6D 7D 8D 9D 10D",
                "KS 7H JC 9S 8H QC 4S 2H 3C 5D 6D 7D 8D 9D 10D");
        table.actions.play(game.getId(), S0, card("AS"), 11);
        table.actions.play(game.getId(), S1, card("7H"), null);
        table.actions.play(game.getId(), new PlayerId(2), card("JC"), null);
        table.actions.play(game.getId(), S0, card("4S"), null);
        WireContract.playLifecycle(new NinetyNineLifecycleBroadcaster(table.broadcaster, table.actions), game, 1);

        contract.messagesTo(table.messaging, 0).verify("lifecycle");
    }
}
