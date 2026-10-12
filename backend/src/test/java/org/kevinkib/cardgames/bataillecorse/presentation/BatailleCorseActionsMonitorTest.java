package org.kevinkib.cardgames.bataillecorse.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseScenario;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.support.ActionUnderMonitorProbe;
import org.kevinkib.cardgames.presentation.support.ActionUnderMonitorProbe.BlockingMessaging;
import org.kevinkib.cardgames.presentation.support.SessionTable;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.*;

/** Bataille Corse actions mutate and broadcast under the monitor of the game (lock order: game, then session). */
class BatailleCorseActionsMonitorTest {

    private final SessionTable table = new SessionTable(new BatailleCorseFactory());
    private final BlockingMessaging messaging = new BlockingMessaging();
    private final BatailleCorseGameActions actions =
            new BatailleCorseGameActions(table.sessions, new BatailleCorseStateBroadcaster(messaging));

    @Test
    void aSecondActionWaitsForTheBroadcastOfTheFirstOne() throws Exception {
        BatailleCorse game = table.install(
                BatailleCorseScenario.game(List.of(TWO, THREE), List.of(FOUR, FIVE)), BatailleCorseFactory.GAME_TYPE);

        boolean overtook = ActionUnderMonitorProbe.secondRanDuringFirstBroadcast(messaging,
                () -> actions.send(game.getId(), new PlayerId(0)),
                () -> actions.send(game.getId(), new PlayerId(1)));

        assertThat(overtook, is(false));
        assertThat(messaging.toGame.size(), is(2));
        assertThat(messaging.toGame.get(0).getEventType(), is("SEND"));
    }
}
