package org.kevinkib.cardgames.bullshit.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.support.ActionUnderMonitorProbe;
import org.kevinkib.cardgames.presentation.support.ActionUnderMonitorProbe.BlockingMessaging;
import org.kevinkib.cardgames.presentation.support.SessionTable;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/** Bullshit actions mutate and broadcast under the monitor of the game (lock order: game, then session). */
class BullshitActionsMonitorTest {

    private final SessionTable table = new SessionTable(new BullshitFactory());
    private final BlockingMessaging messaging = new BlockingMessaging();
    private final BullshitGameActions actions =
            new BullshitGameActions(table.sessions, BullshitBroadcasters.state(messaging));

    @Test
    void aSecondActionWaitsForTheBroadcastOfTheFirstOne() throws Exception {
        Bullshit game = table.install((Bullshit) new BullshitFactory().create(GameId.generate(), 3), "bullshit");
        var card = game.getPlayers().get(0).getCards().get(0);

        boolean overtook = ActionUnderMonitorProbe.secondRanDuringFirstBroadcast(messaging,
                () -> actions.discard(game.getId(), new PlayerId(0), List.of(card)),
                () -> actions.callBullshit(game.getId(), new PlayerId(1)));

        assertThat(overtook, is(false));
        List<String> types = messaging.toSeat(new PlayerId(2)).stream().map(Response::getEventType).toList();
        assertThat(types, is(List.of("DISCARD", "CALL_BULLSHIT")));
    }
}
