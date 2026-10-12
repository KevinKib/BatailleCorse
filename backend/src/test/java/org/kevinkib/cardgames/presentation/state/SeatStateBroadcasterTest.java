package org.kevinkib.cardgames.presentation.state;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.FakeGame;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.SeatNames;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class SeatStateBroadcasterTest {

    private final GameId id = GameId.generate();
    private final FakeGame game = new FakeGame(id, 3);
    private final RecordingMessaging messaging = new RecordingMessaging();

    /** The view is a string that proves which viewer, names and bots reached the projection. */
    private SeatStateBroadcaster<FakeGame, String> broadcaster(BotSeats bots, SeatNames names) {
        return new SeatStateBroadcaster<>(messaging, bots, names,
                (g, viewer, n, b) -> "view" + viewer.id() + n.size() + b.size());
    }

    @Test
    void givenNamesAndBots_whenStateFor_thenTheProjectionReceivesViewerNamesAndBots() {
        SeatStateBroadcaster<FakeGame, String> broadcaster = broadcaster(
                gameId -> Set.of(new PlayerId(2)), gameId -> Map.of(new PlayerId(0), "Ann"));

        assertThat(broadcaster.stateFor(game, new PlayerId(1)), is("view111"));
    }

    @Test
    void givenThreeSeats_whenBroadcast_thenEachSeatReceivesItsOwnSuccessResponseInSeatOrder() {
        SeatStateBroadcaster<FakeGame, String> broadcaster = broadcaster(BotSeats.none(), SeatNames.none());

        broadcaster.broadcast(game, "MOVE", new EmptyEventData(), "msg");

        assertThat(messaging.seats, is(List.of(new PlayerId(0), new PlayerId(1), new PlayerId(2))));
        for (int i = 0; i < 3; i++) {
            Response sent = messaging.payloads.get(i);
            assertThat(sent.isSuccess(), is(true));
            assertThat(sent.getEventType(), is("MOVE"));
            assertThat(sent.getEventData(), is(new EmptyEventData()));
            assertThat(sent.getMessage(), is("msg"));
            assertThat(sent.getState(), is("view" + i + "00"));
        }
    }

    @Test
    void givenBotSeat_whenBroadcast_thenOnlyHumanSeatsReceiveAMessage() {
        SeatStateBroadcaster<FakeGame, String> broadcaster =
                broadcaster(gameId -> Set.of(new PlayerId(1)), SeatNames.none());

        broadcaster.broadcast(game, "MOVE", new EmptyEventData(), "msg");

        assertThat(messaging.seats, is(List.of(new PlayerId(0), new PlayerId(2))));
    }

    @Test
    void givenListeners_whenBroadcast_thenNotifiedAfterTheMessagesWithTheEvent() {
        SeatStateBroadcaster<FakeGame, String> broadcaster = broadcaster(BotSeats.none(), SeatNames.none());
        List<String> notifications = new ArrayList<>();
        List<Integer> messagesAtNotification = new ArrayList<>();
        broadcaster.addListener((g, eventType, data) -> {
            notifications.add(eventType);
            messagesAtNotification.add(messaging.seats.size());
        });

        broadcaster.broadcast(game, "A", new EmptyEventData(), "msg");
        broadcaster.broadcast(game, "B", new EmptyEventData(), "msg");

        assertThat(notifications, is(List.of("A", "B")));
        assertThat(messagesAtNotification, is(List.of(3, 6)));
    }

    @Test
    void givenFailingListener_whenBroadcast_thenOthersAreStillNotified() {
        SeatStateBroadcaster<FakeGame, String> broadcaster = broadcaster(BotSeats.none(), SeatNames.none());
        List<String> notifications = new ArrayList<>();
        broadcaster.addListener((g, eventType, data) -> {
            throw new IllegalStateException("listener bug");
        });
        broadcaster.addListener((g, eventType, data) -> notifications.add(eventType));

        broadcaster.broadcast(game, "A", new EmptyEventData(), "msg");

        assertThat(notifications, is(List.of("A")));
    }
}
