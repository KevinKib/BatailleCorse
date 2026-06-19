package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameMode;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class SeatSubscriptionInterceptorTest {

    private SessionService sessionService;
    private SeatSubscriptionInterceptor interceptor;
    private GameId gameId;
    private String seat0Token;
    private String seat1Token;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
        interceptor = new SeatSubscriptionInterceptor(sessionService);
        gameId = sessionService.createGame("bullshit", 2, GameMode.SOLO).getId();
        seat0Token = sessionService.tokenForSeat(gameId, new PlayerId(0));
        seat1Token = sessionService.tokenForSeat(gameId, new PlayerId(1));
    }

    private Message<byte[]> subscribe(String destination, String token) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (token != null) {
            accessor.setNativeHeader("token", token);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private String seatTopic(String token) {
        return "/topic/game/" + gameId.uuid() + "/seat/" + token;
    }

    @Test
    void givenMatchingToken_whenSubscribeToOwnSeatTopic_thenAllowed() {
        Message<?> result = interceptor.preSend(subscribe(seatTopic(seat0Token), seat0Token), null);

        assertThat(result, is(notNullValue()));
    }

    @Test
    void givenTokenDifferentFromTheTopicToken_thenRejected() {
        // Holding seat 0's token but subscribing to seat 1's token-addressed topic.
        Message<?> result = interceptor.preSend(subscribe(seatTopic(seat1Token), seat0Token), null);

        assertThat(result, is(nullValue()));
    }

    @Test
    void givenNoToken_whenSubscribeToSeatTopic_thenRejected() {
        Message<?> result = interceptor.preSend(subscribe(seatTopic(seat0Token), null), null);

        assertThat(result, is(nullValue()));
    }

    @Test
    void givenStaleTokenThatNoLongerResolves_thenRejected() {
        // A token from a room that has since reopened (or any unknown token) must not authorize.
        String staleToken = UUID.randomUUID().toString();

        Message<?> result = interceptor.preSend(subscribe(seatTopic(staleToken), staleToken), null);

        assertThat(result, is(nullValue()));
    }

    @Test
    void givenPublicDestination_whenSubscribe_thenAllowedWithoutToken() {
        Message<?> result = interceptor.preSend(
                subscribe("/topic/game/" + gameId.uuid(), null), null);

        assertThat(result, is(notNullValue()));
    }
}
