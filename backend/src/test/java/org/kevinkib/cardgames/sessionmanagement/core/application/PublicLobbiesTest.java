package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.PublicLobbies.Entry;
import org.kevinkib.cardgames.sessionmanagement.core.domain.LobbyVisibility;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

class PublicLobbiesTest {

    private static final Instant T0 = Instant.parse("2026-10-26T10:00:00Z");

    private static SessionGame lobby(int seats, int humans, int bots) {
        return lobby(GameId.generate(), seats, humans, bots);
    }

    private static SessionGame lobby(GameId id, int seats, int humans, int bots) {
        SessionGame session = SessionGame.create(id, seats, "kobo");
        for (int i = 0; i < humans; i++) {
            session.claimNextFreeSeat(i == 0 ? "Alice" : null);
        }
        for (int i = 0; i < bots; i++) {
            session.claimBot(null);
        }
        return session;
    }

    private static Entry entry(SessionGame session, boolean started, int minutes) {
        return new Entry(session, started, T0.plusSeconds(60L * minutes));
    }

    private static String id(SessionGame session) {
        return session.id().uuid().toString();
    }

    private static List<String> ids(PublicLobbyList list) {
        return list.lobbies().stream().map(PublicLobby::gameId).toList();
    }

    private static PublicLobbyList list(List<Entry> entries) {
        return PublicLobbies.list("kobo", entries, PublicLobbies.MAX_LISTED, 7L);
    }

    @Test
    void anOpenLobbyIsListedWithItsCounts() {
        SessionGame session = lobby(4, 2, 1);

        PublicLobbyList list = list(List.of(entry(session, false, 0)));

        PublicLobby only = list.lobbies().get(0);
        assertThat(only.gameId(), is(id(session)));
        assertThat(only.gameType(), is("kobo"));
        assertThat(only.hostName(), is("Alice"));
        assertThat(only.joined(), is(3));
        assertThat(only.bots(), is(1));
        assertThat(only.maxPlayers(), is(4));
        assertThat(only.state(), is(PublicLobbyState.OPEN));
        assertThat(only.openedAt(), is(T0));
        assertThat(list.gameType(), is("kobo"));
        assertThat(list.version(), is(7L));
        assertThat(list.total(), is(1));
        assertThat(list.truncated(), is(false));
    }

    @Test
    void aLobbyWithNoFreeSeatIsFull() {
        assertThat(list(List.of(entry(lobby(2, 1, 1), false, 0))).lobbies().get(0).state(),
                is(PublicLobbyState.FULL));
    }

    @Test
    void aStartedGameIsInPlayEvenWithAFreeSeat() {
        assertThat(list(List.of(entry(lobby(4, 2, 0), true, 0))).lobbies().get(0).state(),
                is(PublicLobbyState.IN_PLAY));
    }

    @Test
    void aFinishedGameNotReopenedStaysInPlay() {
        // The session cannot tell a finished game from a running one: both are "started".
        assertThat(list(List.of(entry(lobby(2, 2, 0), true, 0))).lobbies().get(0).state(),
                is(PublicLobbyState.IN_PLAY));
    }

    @Test
    void aPrivateLobbyIsNeverListedNorCounted() {
        SessionGame hidden = lobby(4, 1, 0);
        hidden.setVisibility(LobbyVisibility.PRIVATE);

        PublicLobbyList list = list(List.of(entry(hidden, false, 0)));

        assertThat(list.lobbies(), is(empty()));
        assertThat(list.total(), is(0));
    }

    @Test
    void aLobbyWhoseHumansAreAllOfflineIsHidden() {
        SessionGame abandoned = lobby(4, 2, 1);
        abandoned.players().values().stream().filter(p -> p.isClaimed() && !p.isBot())
                .forEach(p -> abandoned.markConnection(p.token(), false));

        assertThat(list(List.of(entry(abandoned, false, 0))).lobbies(), is(empty()));
    }

    @Test
    void aLobbyWithOneHumanStillOnlineIsKept() {
        SessionGame partlyOnline = lobby(4, 2, 0);
        partlyOnline.markConnection(partlyOnline.players().get(new PlayerId(1)).token(), false);

        assertThat(list(List.of(entry(partlyOnline, false, 0))).lobbies().size(), is(1));
    }

    @Test
    void aStartedGameIsKeptWhateverTheConnections() {
        SessionGame started = lobby(2, 2, 0);
        started.players().values().forEach(p -> started.markConnection(p.token(), false));

        assertThat(list(List.of(entry(started, true, 0))).lobbies().size(), is(1));
    }

    @Test
    void theHostNameIsNullWhenTheHostTypedNone() {
        SessionGame session = SessionGame.create(GameId.generate(), 4, "kobo");
        session.claimNextFreeSeat(null);

        assertThat(list(List.of(entry(session, false, 0))).lobbies().get(0).hostName(), is((String) null));
    }

    @Test
    void openGamesComeFirstNewestFirstThenTheOthersNewestFirst() {
        SessionGame openOld = lobby(4, 1, 0);
        SessionGame openNew = lobby(4, 1, 0);
        SessionGame fullNewest = lobby(2, 1, 1);
        SessionGame inPlayOld = lobby(4, 2, 0);
        SessionGame inPlayMid = lobby(4, 2, 0);

        PublicLobbyList list = list(List.of(
                entry(inPlayOld, true, 1), entry(openOld, false, 2), entry(fullNewest, false, 9),
                entry(openNew, false, 5), entry(inPlayMid, true, 6)));

        assertThat(ids(list), contains(
                id(openNew), id(openOld), id(fullNewest), id(inPlayMid), id(inPlayOld)));
    }

    @Test
    void tiesAreBrokenByGameId() {
        SessionGame b = lobby(new GameId(UUID.fromString("00000000-0000-0000-0000-00000000000b")), 4, 1, 0);
        SessionGame a = lobby(new GameId(UUID.fromString("00000000-0000-0000-0000-00000000000a")), 4, 1, 0);

        assertThat(ids(list(List.of(entry(b, false, 0), entry(a, false, 0)))), contains(id(a), id(b)));
    }

    @Test
    void theListIsCappedAfterSortingWithTheTotalAndTruncated() {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            entries.add(entry(lobby(4, 1, 0), false, i));
        }
        SessionGame extraInPlay = lobby(4, 2, 0);
        entries.add(entry(extraInPlay, true, 100));

        PublicLobbyList list = list(entries);

        assertThat(PublicLobbies.MAX_LISTED, is(50));
        assertThat(list.lobbies().size(), is(50));
        assertThat(list.total(), is(61));
        assertThat(list.truncated(), is(true));
        // The open games win the cap: the started game is cut even though it is the newest.
        assertThat(ids(list).contains(id(extraInPlay)), is(false));
    }

    @Test
    void exactlyTheCapIsNotTruncated() {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            entries.add(entry(lobby(4, 1, 0), false, i));
        }

        PublicLobbyList list = list(entries);

        assertThat(list.lobbies().size(), is(50));
        assertThat(list.truncated(), is(false));
    }
}
