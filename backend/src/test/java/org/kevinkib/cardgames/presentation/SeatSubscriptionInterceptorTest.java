package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.domain.GameMode;
import org.kevinkib.cardgames.sessionmanagement.infrastructure.InMemorySessionRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Clock;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class SeatSubscriptionInterceptorTest {

    private SessionService sessionService;
    private SeatSubscriptionInterceptor interceptor;
    private GameId gameId;
    private String seat0Token;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
        interceptor = new SeatSubscriptionInterceptor(sessionService);
        gameId = sessionService.createGame("bullshit", 2, GameMode.SOLO).getId();
        seat0Token = sessionService.loadTokenByPlayerId(gameId, new PlayerId(0)).uuid().toString();
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

    @Test
    void givenMatchingToken_whenSubscribeToOwnSeat_thenAllowed() {
        Message<?> result = interceptor.preSend(
                subscribe("/topic/game/" + gameId.uuid() + "/seat/0", seat0Token), null);

        assertThat(result, is(notNullValue()));
    }

    @Test
    void givenSeat0Token_whenSubscribeToSeat1_thenRejected() {
        Message<?> result = interceptor.preSend(
                subscribe("/topic/game/" + gameId.uuid() + "/seat/1", seat0Token), null);

        assertThat(result, is(nullValue()));
    }

    @Test
    void givenNoToken_whenSubscribeToSeat_thenRejected() {
        Message<?> result = interceptor.preSend(
                subscribe("/topic/game/" + gameId.uuid() + "/seat/0", null), null);

        assertThat(result, is(nullValue()));
    }

    @Test
    void givenPublicDestination_whenSubscribe_thenAllowedWithoutToken() {
        Message<?> result = interceptor.preSend(
                subscribe("/topic/game/" + gameId.uuid(), null), null);

        assertThat(result, is(notNullValue()));
    }
}
