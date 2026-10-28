package org.kevinkib.cardgames.ninetynine.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A forfeit announces what it caused (elimination, end of the game), not only the forfeit itself. */
class NinetyNineLifecycleBroadcasterTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);

    private final NinetyNineTable table = new NinetyNineTable();
    private final NinetyNineLifecycleBroadcaster lifecycle =
            new NinetyNineLifecycleBroadcaster(table.broadcaster, table.actions);

    private List<String> distinctTypes() {
        return table.messaging.payloads.stream().map(Response::getEventType).distinct().toList();
    }

    @Test
    void aForfeitThatEndsTheGameAnnouncesForfeitEliminatedAndGameOver() throws Exception {
        NinetyNine game = table.atNinetyFour();

        game.forfeit(S1);
        lifecycle.forfeited(game, S1, ForfeitReason.RESIGNED);

        assertThat(distinctTypes()).containsExactly("FORFEIT", "ELIMINATED", "GAME_OVER");
    }

    @Test
    void aForfeitThatLeavesTheGameRunningAnnouncesForfeitAndEliminated() {
        NinetyNine game = table.beforeABust();

        game.forfeit(new PlayerId(2));
        lifecycle.forfeited(game, new PlayerId(2), ForfeitReason.DISCONNECTED);

        assertThat(distinctTypes()).containsExactly("FORFEIT", "ELIMINATED");
    }
}
