package org.kevinkib.cardgames.sessionmanagement.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class SessionLobbyTest {

    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
    }

    @Test
    void givenBullshit_whenCreateRoom_thenMaxSeatsHostClaimedNoGame() {
        SessionGame lobby = service.createRoom("bullshit", "Alice");

        assertThat(lobby.seats().size(), is(6));
        assertThat(lobby.isClaimed(new PlayerId(0)), is(true));
        assertThat(lobby.seats().get(0).name(), is("Alice"));
        assertThat(lobby.isClaimed(new PlayerId(1)), is(false));
        assertThat(service.findGame(lobby.id()).isPresent(), is(false));
    }
}
