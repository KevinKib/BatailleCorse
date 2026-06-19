package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guards private per-seat topics. Per-seat channels are addressed by token (/topic/game/{id}/seat/{token}),
 * so a SUBSCRIBE is allowed only when the frame carries a "token" header equal to the topic's token and that
 * token still resolves to a seat in the game. A stale token (e.g. from a room that has since reopened) no
 * longer resolves, so the subscribe is rejected. Non-seat destinations pass through.
 */
public class SeatSubscriptionInterceptor implements ChannelInterceptor {

    private static final Pattern SEAT_DESTINATION = Pattern.compile("/topic/game/([^/]+)/seat/([^/]+)");

    private final SessionService sessionService;

    public SeatSubscriptionInterceptor(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (!StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }
        Matcher matcher = SEAT_DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            return message;
        }

        String token = accessor.getFirstNativeHeader("token");
        String topicToken = matcher.group(2);
        if (token == null || !token.equals(topicToken)) {
            return null;
        }
        try {
            GameId gameId = new GameId(matcher.group(1));
            return sessionService.findPlayerIdByToken(gameId, token)
                    .map(seat -> message)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
