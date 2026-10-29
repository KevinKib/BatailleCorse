package org.kevinkib.cardgames.president.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.support.WireContract;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.RandomDeckSource;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.domain.rules.MatchLength;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.presentation.dto.event.PresidentEventType;

import java.util.List;

import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFactory.GAME_TYPE;

/** The JSON a President client receives, pinned in {@code contract/president/} (see {@link WireContract}). */
class PresidentWireContractTest {

    private final PresidentTable table = new PresidentTable();

    @Test
    void theEventTypesArePinned() {
        WireContract.verifyEventTypes(GAME_TYPE, PresidentEventType.values());
    }

    /** A seeded three-round match played through the commands: every phase, exchanges included, to the end. */
    @Test
    void aLobbyThenAMatchPlayedToItsEnd() throws Exception {
        WireContract contract = WireContract.of(GAME_TYPE);
        contract.lobby(table.sessions, table.messaging, GAME_TYPE, table.rest::joinGame, table.rest::getGame,
                "Bob", "Cleo");
        President game = table.install(new President(GameId.generate(), 3, 9L, new RandomDeckSource(),
                PresidentOptions.defaults().withLength(MatchLength.ROUNDS_3)));
        for (int step = 0; step < 5000 && !game.isFinished(); step++) {
            if (game.phase() == Phase.PLAYING) {
                PlayerId current = game.currentSeat();
                List<Play> options = game.legalPlays(current);
                if (game.trick().plays().isEmpty() || !options.isEmpty()) {
                    table.actions.play(game.getId(), current, options.get(0).cards());
                } else {
                    table.actions.pass(game.getId(), current);
                }
            } else if (game.phase() == Phase.EXCHANGE) {
                var pending = game.pendingReturns().get(0);
                table.actions.giveBack(game.getId(), pending.high(),
                        game.hand(pending.high()).subList(0, pending.count()));
            } else if (game.phase() == Phase.EXCHANGE_VIEW) {
                int round = game.round();
                table.actions.seeExchange(game.getId(), new PlayerId(0), round);
                table.actions.closeExchangeView(game.getId(), round);
            } else {
                for (PlayerId s : game.getPlayerIds()) {
                    if (!game.readySeats().contains(s)) {
                        table.actions.ready(game.getId(), s);
                        break;
                    }
                }
            }
        }

        contract.messagesTo(table.messaging, 0)
                .rest("state", table.rest.getGame(game.getId().uuid().toString(), table.token(game, 0)).getBody())
                .verify("game");
    }

    @Test
    void theLifecycle() {
        WireContract contract = WireContract.of(GAME_TYPE);
        President game = table.install(aPresident().withHand("4C 9C").withHand("5C 6C").withHand("7C 8C").leader(0)
                .build());
        WireContract.playLifecycle(new SeatLifecycleBroadcaster<>(President.class, table.broadcaster), game, 1);

        contract.messagesTo(table.messaging, 0).verify("lifecycle");
    }
}
