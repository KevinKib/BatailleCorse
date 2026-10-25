package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.sessionmanagement.core.domain.LobbyVisibility;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionPlayer;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * The rules of the public game list, as a pure function: which sessions are listed, in which state, in which order
 * and how many.
 */
public final class PublicLobbies {

    /** At most this many entries are listed per game type; the list says when more exist. */
    public static final int MAX_LISTED = 50;

    private static final Comparator<PublicLobby> ORDER = Comparator
            .comparing((PublicLobby lobby) -> lobby.state() == PublicLobbyState.OPEN ? 0 : 1)
            .thenComparing(Comparator.comparing(PublicLobby::openedAt).reversed())
            .thenComparing(PublicLobby::gameId);

    private PublicLobbies() {
    }

    /** A session as the list sees it: whether its game started, and when its room was first opened. */
    public record Entry(SessionGame session, boolean started, Instant openedAt) {
    }

    /**
     * Lists the public entries: public, and either started or with at least one human online. Open games come
     * first, newest first, then the others newest first, ties broken by game id; only the first {@code max} are kept.
     */
    public static PublicLobbyList list(String gameType, List<Entry> entries, int max, long version) {
        List<PublicLobby> listed = entries.stream()
                .filter(entry -> entry.session().visibility() == LobbyVisibility.PUBLIC)
                .filter(entry -> entry.started() || hasHumanOnline(entry.session()))
                .map(PublicLobbies::toLobby)
                .sorted(ORDER)
                .toList();
        return new PublicLobbyList(gameType, listed.stream().limit(max).toList(), listed.size(),
                listed.size() > max, version);
    }

    private static boolean hasHumanOnline(SessionGame session) {
        return session.players().values().stream()
                .anyMatch(seat -> seat.isClaimed() && !seat.isBot() && !seat.isDisconnected());
    }

    private static PublicLobby toLobby(Entry entry) {
        SessionGame session = entry.session();
        List<SessionPlayer> seats = session.seats();
        int joined = (int) seats.stream().filter(SessionPlayer::isClaimed).count();
        int bots = (int) seats.stream().filter(seat -> seat.isClaimed() && seat.isBot()).count();
        PublicLobbyState state = entry.started() ? PublicLobbyState.IN_PLAY
                : joined < seats.size() ? PublicLobbyState.OPEN : PublicLobbyState.FULL;
        return new PublicLobby(session.id().uuid().toString(), session.gameType(), hostName(session), joined, bots,
                seats.size(), state, entry.openedAt());
    }

    private static String hostName(SessionGame session) {
        SessionPlayer host = session.players().get(SessionGame.HOST_SEAT);
        return host.name() != null ? host.name() : host.defaultName();
    }
}
