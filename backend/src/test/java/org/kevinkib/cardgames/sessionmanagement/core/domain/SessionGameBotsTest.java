package org.kevinkib.cardgames.sessionmanagement.core.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionGameBotsTest {

    private SessionGame lobby;

    @BeforeEach
    void setUp() {
        lobby = SessionGame.create(GameId.generate(), 4, "bullshit");
        lobby.claimHost("Alice");
    }

    @Test
    void givenFreeSeats_whenClaimBot_thenLowestFreeSeatIsFlaggedAndNamedBotOne() {
        SessionPlayer bot = lobby.claimBot(null);

        assertThat(bot.id(), is(new PlayerId(1)));
        assertThat(bot.isBot(), is(true));
        assertThat(bot.isClaimed(), is(true));
        assertThat(bot.name(), is("Bot 1"));
        assertThat(lobby.isBot(new PlayerId(1)), is(true));
        assertThat(lobby.isBot(new PlayerId(0)), is(false));
    }

    @Test
    void givenSeveralBots_whenClaimBot_thenNumberedOverBotsOnly() {
        lobby.claimNextFreeSeat("Bob");

        SessionPlayer first = lobby.claimBot("");
        SessionPlayer second = lobby.claimBot("  ");

        assertThat(first.name(), is("Bot 1"));
        assertThat(second.name(), is("Bot 2"));
    }

    @Test
    void givenBotsAndHumans_thenBotsCountAsClaimedSeats() {
        lobby.claimBot(null);

        assertThat(lobby.claimedCount(), is(2));
    }

    @Test
    void givenFullRoom_whenClaimBot_thenNoFreeSeat() {
        lobby.claimBot(null);
        lobby.claimBot(null);
        lobby.claimBot(null);

        assertThrows(NoFreeSeatException.class, () -> lobby.claimBot(null));
    }

    @Test
    void givenBotSeat_thenItsTokenNeverResolvesAndIsNeverExposed() {
        SessionPlayer bot = lobby.claimBot(null);

        assertThat(lobby.findPlayerByToken(bot.token()).isPresent(), is(false));
        assertThat(lobby.findTokenByPlayer(bot.id()).isPresent(), is(false));
        assertThat(lobby.findTokenByPlayer(new PlayerId(0)).isPresent(), is(true));
    }

    @Test
    void givenBotSeats_thenBotSeatsListsThem() {
        lobby.claimBot(null);
        lobby.claimNextFreeSeat("Bob");
        lobby.claimBot(null);

        assertThat(lobby.botSeats(), is(Set.of(new PlayerId(1), new PlayerId(3))));
    }

    @Test
    void givenBotWithNoHumanAbove_whenRemoveBot_thenSeatIsFreedAndClaimable() {
        lobby.claimBot(null);

        lobby.removeBot(new PlayerId(1));

        assertThat(lobby.isClaimed(new PlayerId(1)), is(false));
        assertThat(lobby.isBot(new PlayerId(1)), is(false));
        assertThat(lobby.claimNextFreeSeat("Bob").id(), is(new PlayerId(1)));
    }

    @Test
    void givenHumanSeat_whenRemoveBot_thenRefused() {
        assertThrows(NotABotSeatException.class, () -> lobby.removeBot(new PlayerId(0)));
    }

    @Test
    void givenUnclaimedSeat_whenRemoveBot_thenRefused() {
        assertThrows(NotABotSeatException.class, () -> lobby.removeBot(new PlayerId(2)));
    }

    @Test
    void givenUnknownSeat_whenRemoveBot_thenRefused() {
        assertThrows(IllegalArgumentException.class, () -> lobby.removeBot(new PlayerId(9)));
    }

    @Test
    void givenHumanSittingAboveTheBot_whenRemoveBot_thenRefusedToKeepSeatsContiguous() {
        lobby.claimBot(null);
        lobby.claimNextFreeSeat("Bob");

        assertThrows(BotRemovalBlockedException.class, () -> lobby.removeBot(new PlayerId(1)));
        assertThat(lobby.isBot(new PlayerId(1)), is(true));
    }

    @Test
    void givenOnlyBotsAbove_whenRemoveBot_thenBotsAboveShiftDownAndAreRenumbered() {
        lobby.claimBot(null);
        lobby.claimBot(null);
        lobby.claimBot(null);

        lobby.removeBot(new PlayerId(1));

        assertThat(lobby.botSeats(), is(Set.of(new PlayerId(1), new PlayerId(2))));
        assertThat(lobby.isClaimed(new PlayerId(3)), is(false));
        assertThat(lobby.seats().get(1).name(), is("Bot 1"));
        assertThat(lobby.seats().get(2).name(), is("Bot 2"));
        assertThat(lobby.claimedCount(), is(3));
    }

    @Test
    void givenMixedSeats_thenRemovableBotSeatsAreTheBotsWithNoHumanAbove() {
        lobby.claimBot(null);
        lobby.claimBot(null);

        assertThat(lobby.removableBotSeats(), contains(new PlayerId(1), new PlayerId(2)));

        lobby.claimNextFreeSeat("Bob");

        assertThat(lobby.removableBotSeats(), is(empty()));
    }

    @Test
    void givenReleasedBotSeat_thenItsTokenStillBelongsToTheSeatButDoesNotResolve() {
        SessionPlayer bot = lobby.claimBot(null);
        lobby.removeBot(bot.id());
        SessionPlayer human = lobby.claimNextFreeSeat("Bob");

        assertThat(human.isBot(), is(false));
        assertThat(lobby.findPlayerByToken(human.token()).get(), is(new PlayerId(1)));
    }

    @Test
    void givenSeatsList_thenBotFlagIsExposedInSeatOrder() {
        lobby.claimBot(null);

        List<Boolean> flags = lobby.seats().stream().map(SessionPlayer::isBot).toList();

        assertThat(flags, contains(false, true, false, false));
    }

    @Test
    void givenBotWithOption_whenSetSeatOption_thenStoredOnTheSeat() {
        SessionPlayer bot = lobby.claimBot(null);

        lobby.setSeatOption(bot.id(), "tier", "3");

        assertThat(lobby.players().get(bot.id()).options(), is(java.util.Map.of("tier", "3")));
    }

    @Test
    void givenHumanOrFreeSeat_whenSetSeatOption_thenNotABotSeat() {
        assertThrows(NotABotSeatException.class, () -> lobby.setSeatOption(new PlayerId(0), "tier", "3"));
        assertThrows(NotABotSeatException.class, () -> lobby.setSeatOption(new PlayerId(2), "tier", "3"));
    }

    @Test
    void givenBotsWithOptions_whenAnEarlierBotIsRemoved_thenLaterBotsKeepTheirOptions() {
        lobby.claimBot(null);
        SessionPlayer second = lobby.claimBot(null);
        SessionPlayer third = lobby.claimBot(null);
        lobby.setSeatOption(second.id(), "tier", "5");
        lobby.setSeatOption(third.id(), "tier", "7");

        lobby.removeBot(new PlayerId(1));

        assertThat(lobby.players().get(new PlayerId(1)).options(), is(java.util.Map.of("tier", "5")));
        assertThat(lobby.players().get(new PlayerId(2)).options(), is(java.util.Map.of("tier", "7")));
        assertThat(lobby.players().get(new PlayerId(3)).options(), is(java.util.Map.of()));
    }

    @Test
    void givenBotWithOption_whenRemoved_thenNoOptionLeaksToTheNextBotInThatSeat() {
        SessionPlayer bot = lobby.claimBot(null);
        lobby.setSeatOption(bot.id(), "tier", "5");
        lobby.removeBot(bot.id());

        SessionPlayer next = lobby.claimBot(null);

        assertThat(next.options(), is(java.util.Map.of()));
    }

    @Test
    void givenHuman_whenRenameSeat_thenTrimmedCutAndBlankRestoresTheDefault() {
        lobby.renameSeat(new PlayerId(0), "  Alicia ");
        assertThat(lobby.players().get(new PlayerId(0)).name(), is("Alicia"));

        lobby.renameSeat(new PlayerId(0), "abcdefghijklmnopqrstuvwxyz");
        assertThat(lobby.players().get(new PlayerId(0)).name(), is("abcdefghijklmnopqrst"));

        lobby.renameSeat(new PlayerId(0), " ");
        assertThat(lobby.players().get(new PlayerId(0)).name(), is("Player 1"));
    }

    @Test
    void givenFreeSeat_whenRenameSeat_thenRefused() {
        assertThrows(IllegalArgumentException.class, () -> lobby.renameSeat(new PlayerId(2), "X"));
    }
}
