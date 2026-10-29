package org.kevinkib.cardgames.bullshit.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.presentation.dto.BullshitDto;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.BullshitEventType;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.presentation.support.SessionTable;
import org.kevinkib.cardgames.presentation.support.WireContract;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;

import static org.kevinkib.cardgames.bullshit.domain.BullshitBuilder.aBullshit;
import static org.kevinkib.cardgames.bullshit.domain.BullshitFactory.GAME_TYPE;
import static org.kevinkib.cardgames.bullshit.domain.BullshitFixtures.playerWithRanks;

/** The JSON a Bullshit client receives, pinned in {@code contract/bullshit/} (see {@link WireContract}). */
class BullshitWireContractTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);

    private final SessionTable table = new SessionTable(new BullshitFactory());
    private final SeatStateBroadcaster<Bullshit, BullshitDto> broadcaster =
            BullshitBroadcasters.state(table.messaging, table.sessions::botSeats, table.sessions::seatNames);
    private final BullshitGameActions actions = new BullshitGameActions(table.sessions, broadcaster);
    private final BullshitRestController rest =
            new BullshitRestController(table.sessions, broadcaster, new LobbyBroadcaster(table.messaging));

    @Test
    void theEventTypesArePinned() {
        WireContract.verifyEventTypes(GAME_TYPE, BullshitEventType.values());
    }

    /** A discard, a call, then a last card that makes a pending winner, called and won. */
    @Test
    void aLobbyThenTheMovesOfAGame() throws Exception {
        WireContract contract = WireContract.of(GAME_TYPE);
        contract.lobby(table.sessions, table.messaging, GAME_TYPE, rest::joinGame, rest::getGame, "Bob", "Cleo");

        Bullshit game = table.install(aBullshit().withPlayers(
                playerWithRanks(0, FrenchRank.ACE, FrenchRank.KING),
                playerWithRanks(1, FrenchRank.TWO, FrenchRank.TWO, FrenchRank.NINE),
                playerWithRanks(2, FrenchRank.THREE)).build(), GAME_TYPE);
        actions.discard(game.getId(), S0, List.of(game.getPlayers().get(0).getCards().get(0)));
        actions.callBullshit(game.getId(), S1);

        Bullshit last = table.install(aBullshit().withPlayers(
                playerWithRanks(0, FrenchRank.ACE),
                playerWithRanks(1, FrenchRank.TWO, FrenchRank.NINE)).build(), GAME_TYPE);
        actions.discard(last.getId(), S0, List.of(last.getPlayers().get(0).getCards().get(0)));
        actions.callBullshit(last.getId(), S1);

        contract.messagesTo(table.messaging, 0)
                .rest("state", rest.getGame(last.getId().uuid().toString(), table.token(last, 0)).getBody())
                .verify("game");
    }

    @Test
    void theLifecycle() {
        WireContract contract = WireContract.of(GAME_TYPE);
        Bullshit game = table.install(aBullshit().withPlayers(
                playerWithRanks(0, FrenchRank.ACE, FrenchRank.KING),
                playerWithRanks(1, FrenchRank.TWO, FrenchRank.NINE)).build(), GAME_TYPE);
        WireContract.playLifecycle(BullshitBroadcasters.lifecycle(broadcaster), game, 1);

        contract.messagesTo(table.messaging, 0).verify("lifecycle");
    }
}
