package org.kevinkib.cardgames.presentation.support;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test double for {@link GameMessagingService}: records what is sent instead of touching a broker.
 *
 * <ul>
 *   <li>{@link #sendToSeat} appends to {@link #seats} and {@link #payloads} (same index);</li>
 *   <li>{@link #sendToGame} appends to {@link #toGame};</li>
 *   <li>{@link #sendToToken} resolves the token back to its seat through the {@link SessionService} given to
 *       {@link #resolvingTokensWith} and records it as a seat message, so lobby views keep per-seat assertions.</li>
 * </ul>
 *
 * Every payload the application sends is a {@link Response}; the double casts it. The lists are synchronized
 * because a real WebSocket thread may write while the test thread reads.
 */
public class RecordingMessaging extends GameMessagingService {

    public final List<PlayerId> seats = Collections.synchronizedList(new ArrayList<>());
    public final List<Response> payloads = Collections.synchronizedList(new ArrayList<>());
    public final List<Response> toGame = Collections.synchronizedList(new ArrayList<>());

    private SessionService resolver;

    public RecordingMessaging() {
        super(null, null);
    }

    public RecordingMessaging(SessionService resolver) {
        this();
        this.resolver = resolver;
    }

    /** Lets {@link #sendToToken} find the seat of a token. Returns this, for fluent wiring. */
    public RecordingMessaging resolvingTokensWith(SessionService resolver) {
        this.resolver = resolver;
        return this;
    }

    @Override
    public void sendToGame(String gameId, Object payload) {
        toGame.add((Response) payload);
    }

    @Override
    public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
        seats.add(seat);
        payloads.add((Response) payload);
    }

    @Override
    public void sendToToken(GameId gameId, String token, Object payload) {
        if (resolver == null) {
            throw new IllegalStateException("sendToToken needs a SessionService: call resolvingTokensWith first");
        }
        sendToSeat(gameId, resolver.findPlayerIdByToken(gameId, token).orElseThrow(), payload);
    }

    /** The last message sent to the game topic. */
    public Response last() {
        return toGame.get(toGame.size() - 1);
    }

    /** What was sent to one seat, in order. */
    public List<Response> toSeat(PlayerId seat) {
        List<Response> sent = new ArrayList<>();
        synchronized (payloads) {
            for (int i = 0; i < seats.size(); i++) {
                if (seats.get(i).equals(seat)) {
                    sent.add(payloads.get(i));
                }
            }
        }
        return sent;
    }

    public void clear() {
        seats.clear();
        payloads.clear();
        toGame.clear();
    }
}
