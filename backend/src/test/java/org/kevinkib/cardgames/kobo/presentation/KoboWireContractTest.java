package org.kevinkib.cardgames.kobo.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.kevinkib.cardgames.kobo.presentation.dto.event.KoboEventType;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.support.WireContract;

import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.ref;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;
import static org.kevinkib.cardgames.kobo.domain.KoboFactory.GAME_TYPE;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

/** The JSON a Kobo client receives, pinned in {@code contract/kobo/} (see {@link WireContract}). */
class KoboWireContractTest {

    private final KoboTable table = new KoboTable();

    @Test
    void theEventTypesArePinned() {
        WireContract.verifyEventTypes(GAME_TYPE, KoboEventType.values());
    }

    /** Several short games from the moves of the other tests: powers, matches, the end of a round and of the game. */
    @Test
    void aLobbyThenTheMovesOfAGame() throws Exception {
        WireContract contract = WireContract.of(GAME_TYPE);
        contract.lobby(table.sessions, table.messaging, GAME_TYPE, table.rest::joinGame, table.rest::getGame, "Bob");

        Kobo peek = table.install(aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .inPhase(Phase.DECISION).withDrawn("9H").build());
        table.actions.discardDrawn(peek.getId(), seat(0));
        table.actions.peek(peek.getId(), seat(0), ref(1, 3));

        Kobo match = table.install(aKobo().withTableau("7S", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .withDiscard("7H").build());
        table.actions.match(match.getId(), seat(0), ref(0, 1), null, card("7H").getRank(), 0);
        table.actions.match(match.getId(), seat(0), ref(0, 0), null, card("7H").getRank(), 0);

        Kobo memorising = table.install(aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .withDiscard("2S").withDrawPile("2D", "3D", "4D").inPhase(Phase.MEMORISING).build());
        table.actions.ready(memorising.getId(), seat(0));
        table.actions.ready(memorising.getId(), seat(1));
        table.actions.draw(memorising.getId(), seat(0));
        table.actions.swapDrawn(memorising.getId(), seat(0), 0);

        Kobo end = table.install(aKobo().withTableau("AH", "KH", "KD", "AD").withTableau("5S", "6S", "9D", "KS")
                .withDiscard("2S").withDrawPile("2D", "3D", "4D").withTotals(0, 100 - 20)
                .inPhase(Phase.TURN_END).currentSeat(1).build());
        table.actions.announceKobo(end.getId(), seat(1));
        table.actions.draw(end.getId(), seat(0));
        table.actions.discardDrawn(end.getId(), seat(0));
        table.actions.endTurn(end.getId(), seat(0));

        Kobo round = table.install(aKobo().withTableau("AH", "KH", "KD", "AD").withTableau("5S", "6S", "9D", "KS")
                .withDiscard("2S").withDrawPile("2D", "3D", "4D").inPhase(Phase.TURN_END).currentSeat(1).build());
        table.actions.announceKobo(round.getId(), seat(1));
        table.actions.draw(round.getId(), seat(0));
        table.actions.discardDrawn(round.getId(), seat(0));
        table.actions.endTurn(round.getId(), seat(0));
        table.actions.ready(round.getId(), seat(0));

        contract.messagesTo(table.messaging, 0)
                .rest("state", table.rest.getGame(end.getId().uuid().toString(), table.token(end, 0)).getBody())
                .verify("game");
    }

    @Test
    void theLifecycle() {
        WireContract contract = WireContract.of(GAME_TYPE);
        Kobo game = table.install(aKobo().withPlayers(2).withDrawPile("9S", "2C").build());
        WireContract.playLifecycle(new SeatLifecycleBroadcaster<>(Kobo.class, table.broadcaster), game, 1);

        contract.messagesTo(table.messaging, 0).verify("lifecycle");
    }
}
