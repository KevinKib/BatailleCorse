package org.kevinkib.cardgames.sessionmanagement.core.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionGameVacateTest {

    private SessionGame lobby;

    @BeforeEach
    void setUp() {
        lobby = SessionGame.create(GameId.generate(), 6, "bullshit");
    }

    private static PlayerId seat(int i) {
        return new PlayerId(i);
    }

    private SessionPlayer human(String name) {
        return lobby.players().values().stream().noneMatch(SessionPlayer::isClaimed)
                ? lobby.claimHost(name)
                : lobby.claimNextFreeSeat(name);
    }

    private List<String> names() {
        return lobby.seats().stream().map(s -> s.isClaimed() ? s.name() : "-").toList();
    }

    private void assertContiguous(int claimed) {
        IntStream.range(0, 6).forEach(i -> assertThat("seat " + i, lobby.isClaimed(seat(i)), is(i < claimed)));
        assertThat(lobby.claimedCount(), is(claimed));
    }

    @Test
    void givenHumansOnly_whenLastLeaves_thenOthersStay() {
        human("Alice");
        human("Bob");
        human("Carol");

        lobby.vacate(seat(2));

        assertThat(names(), is(List.of("Alice", "Bob", "-", "-", "-", "-")));
        assertContiguous(2);
    }

    @Test
    void givenHumansOnly_whenMiddleLeaves_thenLaterOccupantsShiftDownWithTheirToken() {
        human("Alice");
        human("Bob");
        SessionToken carolToken = human("Carol").token();
        SessionToken daveToken = human("Dave").token();

        lobby.vacate(seat(1));

        assertThat(names(), is(List.of("Alice", "Carol", "Dave", "-", "-", "-")));
        assertThat(lobby.findPlayerByToken(carolToken).get(), is(seat(1)));
        assertThat(lobby.findPlayerByToken(daveToken).get(), is(seat(2)));
        assertContiguous(3);
    }

    @Test
    void givenHumansOnly_whenHostLeaves_thenNextHumanBecomesSeatZeroAndHost() {
        human("Alice");
        SessionToken bobToken = human("Bob").token();
        SessionToken carolToken = human("Carol").token();

        lobby.vacate(seat(0));

        assertThat(names(), is(List.of("Bob", "Carol", "-", "-", "-", "-")));
        assertThat(lobby.findPlayerByToken(bobToken).get(), is(seat(0)));
        assertThat(lobby.isHost(lobby.findPlayerByToken(bobToken).get()), is(true));
        assertThat(lobby.findPlayerByToken(carolToken).get(), is(seat(1)));
        assertContiguous(2);
    }

    @Test
    void givenABotAtSeatOne_whenHostLeaves_thenTheHumanAtSeatTwoBecomesHostNeverTheBot() {
        human("Alice");
        lobby.claimBot(null);
        SessionToken carolToken = lobby.claimNextFreeSeat("Carol").token();

        lobby.vacate(seat(0));

        assertThat(lobby.findPlayerByToken(carolToken).get(), is(seat(0)));
        assertThat(lobby.isBot(seat(0)), is(false));
        assertThat(lobby.isBot(seat(1)), is(true));
        assertThat(names(), is(List.of("Carol", "Bot 1", "-", "-", "-", "-")));
        assertContiguous(2);
    }

    @Test
    void givenBotsAfterTheHumans_whenHostLeaves_thenBotsFollowAndAreRenumbered() {
        human("Alice");
        SessionToken bobToken = human("Bob").token();
        lobby.claimBot(null);
        lobby.claimBot(null);

        lobby.vacate(seat(0));

        assertThat(names(), is(List.of("Bob", "Bot 1", "Bot 2", "-", "-", "-")));
        assertThat(lobby.findPlayerByToken(bobToken).get(), is(seat(0)));
        assertContiguous(3);
    }

    @Test
    void givenABotBetweenHumans_whenAGuestLeaves_thenTheBotShiftsWithItsOptionsAndKeepsItsName() {
        human("Alice");
        human("Bob");
        lobby.claimBot(null);
        lobby.setSeatOption(seat(2), "tier", "2");
        SessionToken carolToken = lobby.claimNextFreeSeat("Carol").token();

        lobby.vacate(seat(1));

        assertThat(names(), is(List.of("Alice", "Bot 1", "Carol", "-", "-", "-")));
        assertThat(lobby.isBot(seat(1)), is(true));
        assertThat(lobby.players().get(seat(1)).options().get("tier"), is("2"));
        assertThat(lobby.findPlayerByToken(carolToken).get(), is(seat(2)));
        assertContiguous(3);
    }

    @Test
    void givenBotsAfterTheLeaver_whenAGuestLeaves_thenBotsShiftDownAndAreRenumbered() {
        human("Alice");
        human("Bob");
        lobby.claimBot(null);
        lobby.claimBot(null);

        lobby.vacate(seat(1));

        assertThat(names(), is(List.of("Alice", "Bot 1", "Bot 2", "-", "-", "-")));
        assertContiguous(3);
    }

    @Test
    void givenALeaver_thenItsTokenResolvesToNothingAndFreedSeatsHaveFreshTokens() {
        human("Alice");
        SessionToken bobToken = human("Bob").token();
        SessionToken carolToken = human("Carol").token();

        lobby.vacate(seat(1));

        assertThat(lobby.findPlayerByToken(bobToken).isPresent(), is(false));
        assertThat(lobby.findPlayerByToken(carolToken).get(), is(seat(1)));
        SessionToken freedSeatToken = lobby.players().get(seat(2)).token();
        assertThat(freedSeatToken, is(not(carolToken)));
        assertThat(lobby.findPlayerByToken(freedSeatToken).isPresent(), is(false));
        SessionPlayer newcomer = lobby.claimNextFreeSeat("Eve");
        assertThat(newcomer.token(), is(not(bobToken)));
        assertThat(newcomer.token(), is(not(carolToken)));
    }

    @Test
    void givenDefaultNames_whenAGuestLeaves_thenDefaultHumanNamesAreRenumberedAndChosenOnesKept() {
        human(null);
        human(null);
        human("Carol");
        human(null);

        lobby.vacate(seat(1));

        assertThat(names(), is(List.of("Player 1", "Carol", "Player 3", "-", "-", "-")));
    }

    @Test
    void givenDefaultNames_whenHostLeaves_thenTheNewHostIsPlayerOne() {
        human(null);
        human(null);
        human(null);

        lobby.vacate(seat(0));

        assertThat(names(), is(List.of("Player 1", "Player 2", "-", "-", "-", "-")));
    }

    @Test
    void givenOnlyBotsWouldRemain_whenHostLeaves_thenNoHumanIsLeft() {
        human("Alice");
        lobby.claimBot(null);

        lobby.vacate(seat(0));

        assertThat(names(), is(List.of("Bot 1", "-", "-", "-", "-", "-")));
        assertThat(lobby.players().values().stream().anyMatch(p -> p.isClaimed() && !p.isBot()), is(false));
    }

    @Test
    void givenABotSeatAFreeSeatOrAnUnknownSeat_whenVacate_thenRefused() {
        human("Alice");
        lobby.claimBot(null);

        assertThrows(IllegalArgumentException.class, () -> lobby.vacate(seat(1)));
        assertThrows(IllegalArgumentException.class, () -> lobby.vacate(seat(4)));
        assertThrows(IllegalArgumentException.class, () -> lobby.vacate(seat(9)));
    }
}
