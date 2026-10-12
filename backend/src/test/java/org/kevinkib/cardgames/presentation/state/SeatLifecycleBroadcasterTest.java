package org.kevinkib.cardgames.presentation.state;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.FakeGame;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.SeatNames;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.dto.event.ForfeitEventData;
import org.kevinkib.cardgames.presentation.dto.event.OpponentDisconnectedEventData;
import org.kevinkib.cardgames.presentation.dto.event.OpponentReconnectedEventData;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class SeatLifecycleBroadcasterTest {

    private final FakeGame game = new FakeGame(GameId.generate(), 2);
    private final RecordingMessaging messaging = new RecordingMessaging();
    private final SeatLifecycleBroadcaster<FakeGame> lifecycle = new SeatLifecycleBroadcaster<>(
            FakeGame.class,
            new SeatStateBroadcaster<FakeGame, String>(messaging, BotSeats.none(), SeatNames.none(),
                    (g, viewer, names, bots) -> "view" + viewer.id()));

    @Test
    void supportsOnlyItsOwnGameClass() {
        Game other = new Game() {
            public GameId getId() { return null; }
            public boolean isFinished() { return false; }
            public List<PlayerId> getPlayerIds() { return List.of(); }
            public void forfeit(PlayerId loser) { }
        };

        assertThat(lifecycle.supports(game), is(true));
        assertThat(lifecycle.supports(other), is(false));
    }

    @Test
    void disconnected_broadcastsTheDeadlineToEverySeat() {
        lifecycle.disconnected(game, new PlayerId(1), 123L);

        assertThat(messaging.seats, is(List.of(new PlayerId(0), new PlayerId(1))));
        Response first = messaging.payloads.get(0);
        assertThat(first.getEventType(), is("OPPONENT_DISCONNECTED"));
        assertThat(first.getEventData(), is(new OpponentDisconnectedEventData(1, 123L)));
        assertThat(first.getMessage(), is("Player 1 disconnected."));
        assertThat(first.getState(), is("view0"));
    }

    @Test
    void reconnected_broadcastsTheSeatThatCameBack() {
        lifecycle.reconnected(game, new PlayerId(1));

        Response first = messaging.payloads.get(0);
        assertThat(first.getEventType(), is("OPPONENT_RECONNECTED"));
        assertThat(first.getEventData(), is(new OpponentReconnectedEventData(1)));
        assertThat(first.getMessage(), is("Player 1 reconnected."));
    }

    @Test
    void forfeited_broadcastsTheLoserAndTheReason() {
        lifecycle.forfeited(game, new PlayerId(1), ForfeitReason.RESIGNED);

        Response first = messaging.payloads.get(0);
        assertThat(first.getEventType(), is("FORFEIT"));
        assertThat(first.getEventData(), is(new ForfeitEventData(1, "RESIGNED")));
        assertThat(first.getMessage(), is("Player 1 forfeited."));
        assertThat(messaging.seats.size(), is(2));
    }
}
