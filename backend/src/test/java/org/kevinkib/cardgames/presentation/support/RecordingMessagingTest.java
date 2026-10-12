package org.kevinkib.cardgames.presentation.support;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;

import java.time.Clock;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecordingMessagingTest {

    private static final GameId GAME = GameId.generate();

    private static Response response(String eventType) {
        return new SuccessResponse(eventType, null, null, null);
    }

    @Test
    void givenSeatMessages_whenRecorded_thenSeatsAndPayloadsKeepTheirOrder() {
        RecordingMessaging messaging = new RecordingMessaging();
        Response first = response("A");
        Response second = response("B");

        messaging.sendToSeat(GAME, new PlayerId(1), first);
        messaging.sendToSeat(GAME, new PlayerId(0), second);

        assertThat(messaging.seats, contains(new PlayerId(1), new PlayerId(0)));
        assertThat(messaging.payloads, contains(first, second));
    }

    @Test
    void givenSeatMessages_whenToSeat_thenOnlyThatSeatsMessagesComeBack() {
        RecordingMessaging messaging = new RecordingMessaging();
        Response a = response("A");
        Response b = response("B");
        Response c = response("C");
        messaging.sendToSeat(GAME, new PlayerId(0), a);
        messaging.sendToSeat(GAME, new PlayerId(1), b);
        messaging.sendToSeat(GAME, new PlayerId(0), c);

        assertThat(messaging.toSeat(new PlayerId(0)), contains(a, c));
        assertThat(messaging.toSeat(new PlayerId(2)), is(empty()));
    }

    @Test
    void givenGameMessages_whenLast_thenTheMostRecentOneIsReturned() {
        RecordingMessaging messaging = new RecordingMessaging();
        Response last = response("LAST");

        messaging.sendToGame("g", response("FIRST"));
        messaging.sendToGame("g", last);

        assertThat(messaging.last(), is(sameInstance(last)));
        assertThat(messaging.toGame.size(), is(2));
        assertThat(messaging.payloads, is(empty()));
    }

    @Test
    void givenRecordedMessages_whenClear_thenEverythingIsEmptied() {
        RecordingMessaging messaging = new RecordingMessaging();
        messaging.sendToSeat(GAME, new PlayerId(0), response("A"));
        messaging.sendToGame("g", response("B"));

        messaging.clear();

        assertThat(messaging.seats, is(empty()));
        assertThat(messaging.payloads, is(empty()));
        assertThat(messaging.toGame, is(empty()));
    }

    @Test
    void givenResolver_whenSendToToken_thenItIsRecordedAsTheSeatOfThatToken() {
        SessionService sessions = new SessionService(new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new ThreeMenMorrisFactory())));
        RoomCreated room = sessions.createRoom(ThreeMenMorrisFactory.GAME_TYPE, "Alice");
        String guestToken = sessions.joinRoom(new GameId(room.gameId()), "Bob").token();
        RecordingMessaging messaging = new RecordingMessaging().resolvingTokensWith(sessions);
        Response view = response("JOIN");

        messaging.sendToToken(new GameId(room.gameId()), guestToken, view);

        assertThat(messaging.seats, contains(new PlayerId(1)));
        assertThat(messaging.payloads, contains(view));
    }

    @Test
    void givenNoResolver_whenSendToToken_thenItFailsExplicitly() {
        RecordingMessaging messaging = new RecordingMessaging();

        assertThrows(IllegalStateException.class, () -> messaging.sendToToken(GAME, "t", response("X")));
    }
}
