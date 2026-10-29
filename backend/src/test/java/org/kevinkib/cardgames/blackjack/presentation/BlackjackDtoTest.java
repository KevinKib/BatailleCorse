package org.kevinkib.cardgames.blackjack.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.Blackjack;
import org.kevinkib.cardgames.blackjack.presentation.dto.BlackjackDto;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.dto.CardDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.blackjack.presentation.BlackjackTable.card;

/** The per-seat projection: public facts for everyone, private facts for the viewer, secrets for nobody. */
class BlackjackDtoTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);

    /** Seat 0 has 10S 7C (17), seat 1 has 9H 8C (17), the dealer shows 6D over a hidden 5H; next cards 10D 3H. */
    private static final String HAND_1 = "10S 9H 6D 7C 8C 5H 10D 3H";

    private final BlackjackTable table = new BlackjackTable();

    private static CardDto dto(String code) {
        return CardDto.from(card(code));
    }

    private static BlackjackDto view(Blackjack game, PlayerId viewer, Set<PlayerId> bots) {
        Map<PlayerId, String> names = new HashMap<>();
        game.getPlayerIds().forEach(p -> names.put(p, bots.contains(p) ? null : "Seat " + p.id()));
        return BlackjackDto.forViewer(game, viewer, names, bots, BotSeatOptions.none());
    }

    private static BlackjackDto view(Blackjack game, PlayerId viewer) {
        return view(game, viewer, Set.of());
    }

    @Test
    void duringBettingTheViewerGetsItsLimitsAndNoLastBetYet() throws Exception {
        Blackjack game = table.start(2, HAND_1);

        BlackjackDto dto = view(game, S0);

        assertThat(dto.phase()).isEqualTo("BETTING");
        assertThat(dto.gameType()).isEqualTo("blackjack");
        assertThat(dto.handNumber()).isEqualTo(1);
        assertThat(dto.handsTotal()).isEqualTo(10);
        assertThat(dto.solo()).isFalse();
        assertThat(dto.availableActions()).containsExactly("BET");
        assertThat(dto.betLimits()).isEqualTo(new BlackjackDto.BetLimitsDto(1, 50));
        assertThat(dto.lastBet()).isNull();
        assertThat(dto.options()).isEqualTo(new BlackjackDto.OptionsDto("10", "50", 100, 1));
        assertThat(dto.dealer().upCard()).isNull();
        assertThat(dto.dealer().cards()).isEmpty();
        assertThat(dto.dealer().holeHidden()).isFalse();
        assertThat(dto.lastHand()).isNull();
        assertThat(dto.outcome()).isInstanceOf(BlackjackDto.OutcomeDto.Ongoing.class);
    }

    @Test
    void anotherSeatsBetIsSealedWhileBettingButItsPresenceIsPublic() throws Exception {
        Blackjack game = table.start(2, HAND_1);
        game.bet(S1, 20);

        BlackjackDto mine = view(game, S0);
        BlackjackDto theirs = view(game, S1);

        BlackjackDto.PlayerDto bob = mine.players().get(1);
        assertThat(bob.betPlaced()).isTrue();
        assertThat(bob.bet()).isNull();
        assertThat(mine.players().get(0).betPlaced()).isFalse();
        assertThat(theirs.players().get(1).bet()).isEqualTo(20);
        assertThat(theirs.lastBet()).isEqualTo(20);
        assertThat(theirs.betLimits()).isNull();
        assertThat(theirs.availableActions()).isEmpty();
        assertThat(mine.lastBet()).isNull();
    }

    @Test
    void aSealedBetDoesNotShowThroughTheStackOrTheStanding() throws Exception {
        Blackjack game = table.start(2, HAND_1);
        game.bet(S1, 20);

        BlackjackDto mine = view(game, S0);
        BlackjackDto theirs = view(game, S1);

        // The stack before the deal was public (100): showing 80 would give the sealed amount away.
        assertThat(mine.players().get(1).stack()).isEqualTo(100);
        assertThat(mine.players().get(0).standing()).isEqualTo(1);
        assertThat(mine.players().get(1).standing()).isEqualTo(1);
        // The bettor still sees its own stack and bet apart.
        assertThat(theirs.players().get(1).stack()).isEqualTo(80);
        assertThat(theirs.players().get(1).standing()).isEqualTo(1);
    }

    @Test
    void onceDealtTheBetsArePublicAndTheHoleCardStaysHidden() throws Exception {
        Blackjack game = table.start(2, HAND_1);
        game.bet(S0, 10);
        game.bet(S1, 20);

        BlackjackDto dto = view(game, S1);

        assertThat(dto.phase()).isEqualTo("PLAYING");
        assertThat(dto.players().get(0).bet()).isEqualTo(10);
        assertThat(dto.players().get(1).bet()).isEqualTo(20);
        assertThat(dto.dealer().upCard()).isEqualTo(dto("6D"));
        assertThat(dto.dealer().cards()).containsExactly(dto("6D"));
        assertThat(dto.dealer().holeHidden()).isTrue();
        assertThat(dto.dealer().total()).isEqualTo(6);
        assertThat(dto.dealer().blackjack()).isFalse();
        assertThat(dto.betLimits()).isNull();
        assertThat(dto.players().get(0).hands().get(0).cards()).containsExactly(dto("10S"), dto("7C"));
        assertThat(dto.players().get(0).hands().get(0).total()).isEqualTo(17);
        assertThat(dto.players().get(0).hands().get(0).status()).isEqualTo("ACTIVE");
        assertThat(dto.toString()).doesNotContain("FIVE");
    }

    @Test
    void theAvailableActionsAreTheViewersOwn() throws Exception {
        Blackjack game = table.start(2, HAND_1);
        game.bet(S0, 10);
        game.bet(S1, 20);

        assertThat(view(game, S0).currentSeat()).isZero();
        assertThat(view(game, S0).availableActions()).containsExactly("HIT", "STAND", "DOUBLE");
        assertThat(view(game, S1).availableActions()).isEmpty();
    }

    @Test
    void aSplitShowsBothHandsTheWaitingOneAndTheActiveIndex() throws Exception {
        Blackjack game = table.start(1, "8S 6D 8C 5H 3H 10D 4S");
        game.bet(S0, 10);
        game.split(S0);

        BlackjackDto dto = view(game, S0);

        List<BlackjackDto.HandDto> hands = dto.players().get(0).hands();
        assertThat(hands).hasSize(2);
        assertThat(hands.get(0).cards()).containsExactly(dto("8S"), dto("3H"));
        assertThat(hands.get(0).fromSplit()).isTrue();
        assertThat(hands.get(1).status()).isEqualTo("WAITING");
        assertThat(dto.activeHandIndex()).isZero();
        assertThat(dto.availableActions()).containsExactly("HIT", "STAND", "DOUBLE");
    }

    @Test
    void aSettledHandIsKeptAsLastHandDuringTheNextBettingWithTheBestHand() throws Exception {
        Blackjack game = table.start(2, "10S 9H 6D 7C 8C 5H 10D 3H");
        game.bet(S0, 10);
        game.bet(S1, 20);
        game.stand(S0);
        game.stand(S1);

        BlackjackDto dto = view(game, S0);

        assertThat(dto.phase()).isEqualTo("BETTING");
        assertThat(dto.handNumber()).isEqualTo(2);
        assertThat(dto.dealer().cards()).isEmpty();
        assertThat(dto.players().get(0).hands()).isEmpty();
        assertThat(dto.lastBet()).isEqualTo(10);
        BlackjackDto.LastHandDto last = dto.lastHand();
        assertThat(last.handNumber()).isEqualTo(1);
        assertThat(last.dealer().cards()).containsExactly(dto("6D"), dto("5H"), dto("10D"));
        assertThat(last.dealer().total()).isEqualTo(21);
        assertThat(last.dealer().bust()).isFalse();
        assertThat(last.seats()).hasSize(2);
        assertThat(last.seats().get(0).net()).isEqualTo(-10);
        assertThat(last.seats().get(0).hands().get(0).status()).isEqualTo("STOOD");
        assertThat(last.seats().get(1).net()).isEqualTo(-20);
        assertThat(last.bestSeats()).isEmpty();
    }

    @Test
    void aWinnerIsTheBestHandOfTheLastHand() throws Exception {
        Blackjack game = table.start(2, "10S 9H 6D 7C 8C 5H 5D 10C");
        game.bet(S0, 10);
        game.bet(S1, 20);
        game.stand(S0);
        game.stand(S1);

        assertThat(view(game, S1).lastHand().dealer().bust()).isTrue();
        assertThat(view(game, S1).lastHand().bestSeats()).containsExactly(1);
    }

    @Test
    void aSoloGameEndsWithItsNetAndNoPlacesAndTheHandsKeepTheirNet() throws Exception {
        Blackjack game = table.start(1, GameOptions.of(Map.of("maxBet", "100")), "10S 6D 7C 5H 10D");
        game.bet(S0, 100);
        game.stand(S0);

        BlackjackDto dto = view(game, S0);

        assertThat(dto.phase()).isEqualTo("FINISHED");
        assertThat(dto.solo()).isTrue();
        BlackjackDto.OutcomeDto.Finished outcome = (BlackjackDto.OutcomeDto.Finished) dto.outcome();
        assertThat(outcome.reason()).isEqualTo("BROKE");
        assertThat(outcome.places()).isEmpty();
        assertThat(outcome.stacks()).containsEntry("0", 0);
        assertThat(outcome.net()).isEqualTo(-100);
        assertThat(dto.players().get(0).eliminated()).isTrue();
        assertThat(dto.players().get(0).hands().get(0).net()).isEqualTo(-100);
        assertThat(dto.availableActions()).isEmpty();
    }

    @Test
    void aTournamentEndsWithSharedPlacesByStack() throws Exception {
        Blackjack game = table.start(2, GameOptions.of(Map.of("hands", "5")), "10S 9H 6D 7C 8C 5H 5D");
        game.forfeit(S1);

        BlackjackDto.OutcomeDto.Finished outcome = (BlackjackDto.OutcomeDto.Finished) view(game, S0).outcome();

        assertThat(outcome.reason()).isEqualTo("FORFEIT");
        assertThat(outcome.places()).containsEntry("0", 1).containsEntry("1", 2);
        assertThat(outcome.net()).isNull();
        assertThat(view(game, S0).players().get(1).forfeited()).isTrue();
        assertThat(view(game, S0).players().get(0).place()).isEqualTo(1);
    }

    @Test
    void botsAreMarkedWithTheirLevelAndNoName() {
        Blackjack game = table.start(2, HAND_1);

        BlackjackDto dto = view(game, S0, Set.of(S1));

        assertThat(dto.players().get(1).bot()).isTrue();
        assertThat(dto.players().get(1).level()).isEqualTo("medium");
        assertThat(dto.players().get(1).name()).isNull();
        assertThat(dto.players().get(0).bot()).isFalse();
        assertThat(dto.players().get(0).level()).isNull();
    }

    @Test
    void theStandingIsTheCurrentRankByStackAndZeroForASeatThatIsOut() throws Exception {
        Blackjack game = table.start(3, GameOptions.none(), "10S 9H 5S 6D 7C 8C 9S 5H 5D");
        game.bet(S0, 10);
        game.bet(S1, 20);
        game.bet(new PlayerId(2), 30);

        BlackjackDto dto = view(game, S0);

        assertThat(dto.players().stream().map(BlackjackDto.PlayerDto::standing)).containsExactly(1, 2, 3);
    }
}
