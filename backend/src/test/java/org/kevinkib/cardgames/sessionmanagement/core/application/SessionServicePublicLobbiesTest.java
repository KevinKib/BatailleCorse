package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The public game list as built by the session service, and the notifications that keep it fresh. */
class SessionServicePublicLobbiesTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-26T10:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private MutableClock clock;
    private SessionService service;
    private final List<String> notified = new ArrayList<>();
    private final List<Boolean> monitorHeld = new ArrayList<>();

    private GameId id;
    private String hostToken;
    private String guestToken;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        service = new SessionService(new InMemorySessionRepository(clock),
                new GameFactories(List.of(new BullshitFactory(), new TieredFactory(), new BatailleCorseFactory())));
        RoomCreated room = service.createRoom("bullshit", "Alice");
        id = new GameId(room.gameId());
        hostToken = room.hostToken();
        service.setPublicLobbiesListener(type -> {
            notified.add(type);
            monitorHeld.add(Thread.holdsLock(service));
        });
    }

    private List<String> listedIds(String type) {
        return service.publicLobbies(type).lobbies().stream().map(PublicLobby::gameId).toList();
    }

    private PublicLobby listed() {
        return service.publicLobbies("bullshit").lobbies().get(0);
    }

    private void joinGuest() {
        guestToken = service.joinRoom(id, "Bob").token();
        notified.clear();
        monitorHeld.clear();
    }

    private void assertNotifiedOnce() {
        assertThat(notified, contains("bullshit"));
        assertThat(monitorHeld, contains(false));
    }

    // ---- the list itself

    @Test
    void aFreshLobbyIsListedAsOpenWithItsHost() {
        PublicLobby entry = listed();

        assertThat(entry.gameId(), is(id.uuid().toString()));
        assertThat(entry.hostName(), is("Alice"));
        assertThat(entry.state(), is(PublicLobbyState.OPEN));
        assertThat(entry.joined(), is(1));
        assertThat(entry.maxPlayers(), is(6));
    }

    @Test
    void theListOfAnotherGameTypeIsSeparate() {
        assertThat(service.publicLobbies("tiered").lobbies(), is(empty()));
        assertThat(service.publicLobbies("bataille-corse").lobbies(), is(empty()));
    }

    @Test
    void anUnknownGameTypeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.publicLobbies("chess"));
    }

    @Test
    void aFullLobbyIsListedAsFullAndBotsAreCounted() {
        service.addBot(id, hostToken);
        service.addBot(id, hostToken);
        service.addBot(id, hostToken);
        service.addBot(id, hostToken);
        service.joinRoom(id, "Bob");

        PublicLobby entry = listed();

        assertThat(entry.state(), is(PublicLobbyState.FULL));
        assertThat(entry.joined(), is(6));
        assertThat(entry.bots(), is(4));
    }

    @Test
    void aStartedGameIsInPlayAndAReopenedOneIsOpenAgain() {
        joinGuest();
        service.startGame(id, hostToken);

        assertThat(listed().state(), is(PublicLobbyState.IN_PLAY));

        service.playAgain(id, "Alice");

        assertThat(listed().state(), is(PublicLobbyState.OPEN));
    }

    @Test
    void aLobbyWithNoHumanOnlineIsHiddenUntilOneComesBack() {
        String hostConnection = hostToken;

        service.markSeatConnection(id, hostConnection, false);
        assertThat(listedIds("bullshit"), is(empty()));

        service.markSeatConnection(id, hostConnection, true);
        assertThat(listedIds("bullshit").size(), is(1));
    }

    @Test
    void openedAtOrdersTheNewestFirstAndSurvivesPlayAgain() {
        clock.advance(Duration.ofMinutes(1));
        RoomCreated second = service.createRoom("bullshit", "Zoe");

        assertThat(listedIds("bullshit"), contains(second.gameId(), id.uuid().toString()));

        // Reopening the older room keeps its place in the order: it is still the older one.
        joinGuest();
        service.startGame(id, hostToken);
        clock.advance(Duration.ofMinutes(5));
        service.playAgain(id, "Alice");

        assertThat(listedIds("bullshit"), contains(second.gameId(), id.uuid().toString()));
    }

    @Test
    void theListVersionGrowsWithEveryChange() {
        long before = service.publicLobbies("bullshit").version();

        service.joinRoom(id, "Bob");

        assertThat(service.publicLobbies("bullshit").version(), greaterThan(before));
        long afterJoin = service.publicLobbies("bullshit").version();
        assertThat(service.publicLobbies("bullshit").version(), is(afterJoin));
    }

    // ---- privacy

    @Test
    void createRoomWithThePrivateFlagIsNeverListed() {
        RoomCreated created = service.createRoom("bullshit", "Dana", GameOptions.none(), true);

        assertThat(listedIds("bullshit").contains(created.gameId()), is(false));
        assertThat(service.lobbyView(new GameId(created.gameId()), created.hostToken()).privateGame(), is(true));
    }

    @Test
    void theOlderCreateRoomOverloadsStayPublic() {
        RoomCreated plain = service.createRoom("bullshit", "Dana");
        RoomCreated withOptions = service.createRoom("bullshit", "Eve", GameOptions.none());
        RoomCreated explicit = service.createRoom("bullshit", "Fay", GameOptions.none(), false);

        assertThat(listedIds("bullshit").containsAll(List.of(plain.gameId(), withOptions.gameId(), explicit.gameId())),
                is(true));
        assertThat(service.lobbyView(new GameId(plain.gameId()), plain.hostToken()).privateGame(), is(false));
    }

    @Test
    void setPrivateHidesTheLobbyAndEverySeatSeesIt() {
        joinGuest();

        List<AddressedLobbyView> views = service.setPrivate(id, hostToken, true);

        assertThat(listedIds("bullshit"), is(empty()));
        assertThat(views.size(), is(2));
        assertThat(views.stream().allMatch(v -> v.view().privateGame()), is(true));
        assertThat(service.lobbyView(id, guestToken).privateGame(), is(true));
    }

    @Test
    void setPrivateBumpsTheLobbyVersion() {
        long before = service.lobbyView(id, hostToken).lobbyVersion();

        service.setPrivate(id, hostToken, true);

        assertThat(service.lobbyView(id, hostToken).lobbyVersion(), greaterThan(before));
    }

    @Test
    void settingItBackToPublicListsTheLobbyAgain() {
        service.setPrivate(id, hostToken, true);

        service.setPrivate(id, hostToken, false);

        assertThat(listedIds("bullshit").size(), is(1));
        assertThat(service.lobbyView(id, hostToken).privateGame(), is(false));
    }

    @Test
    void setPrivateIsHostOnly() {
        joinGuest();

        assertThrows(NotHostException.class, () -> service.setPrivate(id, guestToken, true));
        assertThrows(NotHostException.class, () -> service.setPrivate(id, "not-a-token", true));
        assertThat(listedIds("bullshit").size(), is(1));
        assertThat(notified, is(empty()));
    }

    @Test
    void setPrivateIsRefusedOnceTheGameStarted() {
        joinGuest();
        service.startGame(id, hostToken);

        assertThrows(GameAlreadyStartedException.class, () -> service.setPrivate(id, hostToken, true));
    }

    @Test
    void privacyIsKeptThroughOptionChangesAndPlayAgain() {
        service.setPrivate(id, hostToken, true);

        service.updateOptions(id, hostToken, Map.of("claimMode", "suit"));
        assertThat(service.lobbyView(id, hostToken).privateGame(), is(true));

        service.joinRoom(id, "Bob");
        service.startGame(id, hostToken);
        JoinResult again = service.playAgain(id, "Alice");

        assertThat(service.lobbyView(id, again.token()).privateGame(), is(true));
        assertThat(listedIds("bullshit"), is(empty()));
    }

    // ---- notifications

    @Test
    void createRoomNotifiesOnceWithTheGameType() {
        service.createRoom("bullshit", "Dana");

        assertNotifiedOnce();
    }

    @Test
    void joinNotifiesOnce() {
        service.joinRoom(id, "Bob");

        assertNotifiedOnce();
    }

    @Test
    void playAgainNotifiesOnce() {
        joinGuest();
        service.startGame(id, hostToken);
        notified.clear();
        monitorHeld.clear();

        service.playAgain(id, "Alice");

        assertNotifiedOnce();
    }

    @Test
    void addAndRemoveBotNotifyOnceEach() {
        PlayerId bot = service.addBot(id, hostToken);
        assertNotifiedOnce();

        notified.clear();
        monitorHeld.clear();
        service.removeBot(id, hostToken, bot.id());
        assertNotifiedOnce();
    }

    @Test
    void theViewVariantsOfBotUseCasesNotifyOnceToo() {
        service.addBotViews(id, hostToken);
        assertNotifiedOnce();

        notified.clear();
        monitorHeld.clear();
        service.removeBotViews(id, hostToken, 1);
        assertNotifiedOnce();
    }

    @Test
    void removeSeatNotifiesOnce() {
        joinGuest();

        service.removeSeat(id, hostToken, 1);

        assertNotifiedOnce();
    }

    @Test
    void leaveNotifiesOnceAndSoDoesTheRemovalOfTheLastHuman() {
        joinGuest();
        service.leaveLobby(id, guestToken);
        assertNotifiedOnce();

        notified.clear();
        monitorHeld.clear();
        List<GameId> removed = new ArrayList<>();
        service.setLobbyRemovalListener(removed::add);
        service.leaveLobby(id, hostToken);

        assertNotifiedOnce();
        assertThat(removed, contains(id));
        assertThat(listedIds("bullshit"), is(empty()));
    }

    @Test
    void renameNotifiesOnce() {
        service.renameSeat(id, hostToken, "Alicia");

        assertNotifiedOnce();
        assertThat(listed().hostName(), is("Alicia"));
    }

    @Test
    void setPrivateNotifiesOnce() {
        service.setPrivate(id, hostToken, true);

        assertNotifiedOnce();
    }

    @Test
    void startNotifiesOnce() {
        joinGuest();

        service.startGame(id, hostToken);

        assertNotifiedOnce();
    }

    @Test
    void startWithTheTypedOverloadNotifiesOnceToo() {
        joinGuest();

        service.startGame(id, hostToken, org.kevinkib.cardgames.bullshit.domain.Bullshit.class);

        assertNotifiedOnce();
    }

    @Test
    void aChangedConnectionMarkNotifiesOnceAndAnUnchangedOneDoesNot() {
        joinGuest();

        service.markSeatConnection(id, guestToken, false);
        assertNotifiedOnce();

        notified.clear();
        monitorHeld.clear();
        service.markSeatConnection(id, guestToken, false);
        service.markSeatConnection(id, "unknown-token", true);

        assertThat(notified, is(empty()));
    }

    @Test
    void optionsDoNotNotify() {
        service.updateOptions(id, hostToken, Map.of("claimMode", "suit"));

        assertThat(notified, is(empty()));
    }

    @Test
    void seatOptionsDoNotNotify() {
        RoomCreated tiered = service.createRoom(TieredFactory.GAME_TYPE, "Tina");
        GameId tieredId = new GameId(tiered.gameId());
        PlayerId bot = service.addBot(tieredId, tiered.hostToken());
        notified.clear();

        service.setSeatOption(tieredId, tiered.hostToken(), bot.id(), "tier", "2");

        assertThat(notified, is(empty()));
    }

    @Test
    void aRefusedCommandDoesNotNotify() {
        assertThrows(NotHostException.class, () -> service.addBot(id, "not-a-token"));

        assertThat(notified, is(empty()));
    }

    @Test
    void notifyingEveryPublicListTellsEachKnownGameTypeOnce() {
        service.publicListsChanged();

        assertThat(notified.stream().sorted().toList(), contains("bataille-corse", "bullshit", TieredFactory.GAME_TYPE));
        assertThat(monitorHeld.contains(true), is(false));
    }

    @Test
    void anEvictedLobbyDisappearsFromTheListAndNotifies() {
        InMemorySessionRepository repository = new InMemorySessionRepository(clock);
        SessionService local = new SessionService(repository, new GameFactories(List.of(new BullshitFactory())));
        List<String> types = new ArrayList<>();
        local.setPublicLobbiesListener(types::add);
        local.createRoom("bullshit", "Alice");
        types.clear();
        clock.advance(GameCleanupService.IDLE_TTL.plusMinutes(1));

        GameCleanupService cleanup = new GameCleanupService(repository, List.of(new PublicLobbiesEvictionNotifier(local)));
        cleanup.sweep();

        assertThat(types, contains("bullshit"));
        assertThat(local.publicLobbies("bullshit").lobbies(), is(empty()));
    }
}
