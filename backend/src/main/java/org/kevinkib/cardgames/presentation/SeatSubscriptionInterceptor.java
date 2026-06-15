package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionToken;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guards private per-seat topics. A SUBSCRIBE to /topic/game/{id}/seat/{seat} is allowed only when the
 * frame carries a "token" header that resolves to that exact seat. Non-seat destinations pass through.
 */
public class SeatSubscriptionInterceptor implements ChannelInterceptor {

    private static final Pattern SEAT_DESTINATION = Pattern.compile("/topic/game/([^/]+)/seat/(\\d+)");

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
        if (token == null) {
            return null;
        }
        try {
            GameId gameId = new GameId(matcher.group(1));
            int seatId = Integer.parseInt(matcher.group(2));
            return sessionService.findPlayerIdByToken(gameId, new SessionToken(token))
                    .filter(seat -> seat.id() == seatId)
                    .map(seat -> message)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
