package org.kevinkib.cardgames.sessionmanagement.core.infrastructure;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;

/** What the repository records for the public game list: the time a room was first opened, and the rooms of a type. */
class InMemorySessionRepositoryPublicListTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-26T10:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private final MutableClock clock = new MutableClock();
    private final InMemorySessionRepository repository = new InMemorySessionRepository(clock);
    private final Instant opened = clock.instant();

    @Test
    void openedAtIsTheFirstTimeTheLobbyWasSaved() {
        SessionGame lobby = SessionGame.create(GameId.generate(), 2, "kobo");

        repository.saveLobby(lobby);

        assertThat(repository.openedAt(lobby.id()), is(opened));
    }

    @Test
    void savingTheLobbyAgainKeepsOpenedAt() {
        SessionGame lobby = SessionGame.create(GameId.generate(), 2, "kobo");
        repository.saveLobby(lobby);
        clock.advance(Duration.ofMinutes(3));

        repository.saveLobby(lobby);

        assertThat(repository.openedAt(lobby.id()), is(opened));
    }

    @Test
    void startingTheGameKeepsOpenedAt() {
        GameId id = GameId.generate();
        SessionGame lobby = SessionGame.create(id, 2, "bataille-corse");
        repository.saveLobby(lobby);
        clock.advance(Duration.ofMinutes(3));

        repository.save(new BatailleCorse(id, 2), lobby);

        assertThat(repository.openedAt(id), is(opened));
    }

    @Test
    void reopeningARoomKeepsOpenedAtAndDropsTheGame() {
        GameId id = GameId.generate();
        SessionGame lobby = SessionGame.create(id, 2, "bataille-corse");
        repository.saveLobby(lobby);
        repository.save(new BatailleCorse(id, 2), lobby);
        clock.advance(Duration.ofMinutes(3));

        repository.reopen(SessionGame.create(id, 2, "bataille-corse"));

        assertThat(repository.openedAt(id), is(opened));
        assertThat(repository.findGame(id).isPresent(), is(false));
    }

    @Test
    void removeClearsOpenedAtSoANewRoomOfTheSameIdStartsAfresh() {
        GameId id = GameId.generate();
        repository.saveLobby(SessionGame.create(id, 2, "kobo"));
        repository.remove(id);
        clock.advance(Duration.ofMinutes(3));

        repository.saveLobby(SessionGame.create(id, 2, "kobo"));

        assertThat(repository.openedAt(id), is(opened.plus(Duration.ofMinutes(3))));
    }

    @Test
    void sessionsOfTypeReturnsLobbiesAndStartedGamesOfThatTypeOnly() {
        SessionGame kobo = SessionGame.create(GameId.generate(), 2, "kobo");
        SessionGame bullshit = SessionGame.create(GameId.generate(), 2, "bullshit");
        GameId startedId = GameId.generate();
        SessionGame started = SessionGame.create(startedId, 2, "kobo");
        repository.saveLobby(kobo);
        repository.saveLobby(bullshit);
        repository.saveLobby(started);

        List<SessionGame> kobos = repository.sessionsOfType("kobo");

        assertThat(kobos, containsInAnyOrder(kobo, started));
        assertThat(repository.sessionsOfType("president").isEmpty(), is(true));
    }
}
