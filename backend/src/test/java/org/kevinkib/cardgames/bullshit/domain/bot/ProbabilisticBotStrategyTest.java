package org.kevinkib.cardgames.bullshit.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.claim.AscendingRankClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.CyclingSuitClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.RankTarget;
import org.kevinkib.cardgames.bullshit.domain.claim.SuitTarget;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.kevinkib.cardgames.bullshit.domain.bot.ObservationFixtures.anObservation;
import static org.kevinkib.cardgames.bullshit.domain.bot.ObservationFixtures.card;

class ProbabilisticBotStrategyTest {

    private static final PlayerId ME = new PlayerId(0);
    private static final RankTarget ACE = new RankTarget(FrenchRank.ACE);

    private static ProbabilisticBotStrategy rankStrategy(double... script) {
        return new ProbabilisticBotStrategy(new AscendingRankClaimMode(), new ScriptedRandom(script), BotTuning.DEFAULT);
    }

    private static BotMemory memory() {
        return new BotMemory(ME);
    }

    /** The bot holds every ace, so a claim of an ace by someone else cannot be true. */
    private static BotObservation impossibleClaim(int currentPlayer) {
        return anObservation()
                .hand(card(FrenchRank.ACE, FrenchSuit.HEART), card(FrenchRank.ACE, FrenchSuit.SPADE),
                        card(FrenchRank.ACE, FrenchSuit.DIAMOND), card(FrenchRank.ACE, FrenchSuit.CLUB))
                .target(new RankTarget(FrenchRank.TWO))
                .claim(1, ACE, 1)
                .currentPlayer(currentPlayer)
                .build();
    }

    /** The claimant held 31 cards before playing two: two aces is the likeliest holding. */
    private static BotObservation almostCertainTruth() {
        return anObservation()
                .hand(card(FrenchRank.TWO, FrenchSuit.HEART), card(FrenchRank.THREE, FrenchSuit.HEART),
                        card(FrenchRank.FOUR, FrenchSuit.HEART), card(FrenchRank.FIVE, FrenchSuit.HEART),
                        card(FrenchRank.SIX, FrenchSuit.HEART))
                .target(new RankTarget(FrenchRank.TWO))
                .handCount(1, 29)
                .claim(1, ACE, 2)
                .currentPlayer(2)
                .build();
    }

    @Test
    void givenPendingWinnerAndBotIsNext_thenAlwaysCalls() {
        BotObservation obs = anObservation()
                .hand(card(FrenchRank.TWO, FrenchSuit.HEART))
                .target(new RankTarget(FrenchRank.TWO))
                .handCount(1, 0)
                .claim(1, ACE, 1)
                .pendingWinner(1)
                .currentPlayer(0)
                .build();

        BotDecision decision = rankStrategy(0.999).decide(obs, memory());

        assertThat(decision, instanceOf(BotDecision.CallBullshit.class));
    }

    @Test
    void givenImpossibleClaim_thenCallsBelowTheCapAndDoesNotAbove() {
        assertThat(rankStrategy(0.29).decide(impossibleClaim(2), memory()), instanceOf(BotDecision.CallBullshit.class));
        assertThat(rankStrategy(0.31).decide(impossibleClaim(2), memory()), instanceOf(BotDecision.Pass.class));
    }

    @Test
    void givenImpossibleClaimNotCalledAndItIsMyTurn_thenPlaysInstead() {
        BotDecision decision = rankStrategy(0.5, 0.5, 0.5).decide(impossibleClaim(0), memory());

        assertThat(decision, instanceOf(BotDecision.Discard.class));
    }

    @Test
    void givenVeryPlausibleClaim_thenDoesNotCallAboveTheFloor() {
        assertThat(rankStrategy(0.5).decide(almostCertainTruth(), memory()), instanceOf(BotDecision.Pass.class));
        assertThat(rankStrategy(0.04).decide(almostCertainTruth(), memory()), instanceOf(BotDecision.CallBullshit.class));
    }

    @Test
    void givenKnownRevealedMatches_thenClaimBecomesCertainAndIsNotChallengedAboveTheFloor() {
        BotMemory memory = memory();
        memory.onReveal(new PlayerId(1), List.of(card(FrenchRank.ACE, FrenchSuit.HEART), card(FrenchRank.ACE, FrenchSuit.SPADE)));
        BotObservation obs = anObservation()
                .hand(card(FrenchRank.TWO, FrenchSuit.HEART))
                .target(new RankTarget(FrenchRank.TWO))
                .handCount(1, 3)
                .claim(1, ACE, 2)
                .currentPlayer(2)
                .build();

        assertThat(rankStrategy(0.06).decide(obs, memory), instanceOf(BotDecision.Pass.class));
    }

    @Test
    void givenMyOwnClaim_thenNeverCallsAndPasses() {
        BotObservation obs = anObservation()
                .hand(card(FrenchRank.TWO, FrenchSuit.HEART))
                .claim(0, ACE, 1)
                .currentPlayer(1)
                .build();

        assertThat(rankStrategy().decide(obs, memory()), instanceOf(BotDecision.Pass.class));
    }

    @Test
    void givenNotMyTurnAndNoClaim_thenPasses() {
        BotObservation obs = anObservation().hand(card(FrenchRank.TWO, FrenchSuit.HEART)).currentPlayer(1).build();

        assertThat(rankStrategy().decide(obs, memory()), instanceOf(BotDecision.Pass.class));
    }

    @Test
    void givenMatchingCardsAndHonestBranch_thenPlaysAllMatchingOnly() {
        BotObservation obs = anObservation()
                .hand(card(FrenchRank.ACE, FrenchSuit.HEART), card(FrenchRank.ACE, FrenchSuit.SPADE),
                        card(FrenchRank.KING, FrenchSuit.CLUB))
                .build();

        BotDecision decision = rankStrategy(0.5).decide(obs, memory());

        assertThat(decision, is(new BotDecision.Discard(List.of(
                card(FrenchRank.ACE, FrenchSuit.HEART), card(FrenchRank.ACE, FrenchSuit.SPADE)))));
    }

    @Test
    void givenMatchingCardsAndBluffBranch_thenAddsOneNonMatchingCard() {
        BotObservation obs = anObservation()
                .hand(card(FrenchRank.ACE, FrenchSuit.HEART), card(FrenchRank.ACE, FrenchSuit.SPADE),
                        card(FrenchRank.KING, FrenchSuit.CLUB))
                .build();

        BotDecision decision = rankStrategy(0.1).decide(obs, memory());

        assertThat(decision, is(new BotDecision.Discard(List.of(
                card(FrenchRank.ACE, FrenchSuit.HEART), card(FrenchRank.ACE, FrenchSuit.SPADE),
                card(FrenchRank.KING, FrenchSuit.CLUB)))));
    }

    @Test
    void givenWholeHandMatchingAndAtMostFour_thenPlaysItWholeWithoutBluffingOrDrawing() {
        BotObservation obs = anObservation()
                .hand(card(FrenchRank.ACE, FrenchSuit.HEART), card(FrenchRank.ACE, FrenchSuit.SPADE))
                .build();

        ScriptedRandom noDraws = new ScriptedRandom();
        BotDecision decision = new ProbabilisticBotStrategy(new AscendingRankClaimMode(), noDraws, BotTuning.DEFAULT)
                .decide(obs, memory());

        assertThat(decision, is(new BotDecision.Discard(obs.hand())));
        assertThat(noDraws.drawn(), is(0));
    }

    @Test
    void givenNoMatchingCard_thenLiesWithTheScriptedNumberOfCardsKeepingSoonNeededOnes() {
        // Three seats, target ACE: the bot's own turns meet targets 3, 6, 9... steps ahead, so with a
        // big hand the card whose rank lands on one of its turns last (EIGHT, then FIVE, TWO) goes first.
        BotObservation obs = anObservation()
                .hand(card(FrenchRank.TWO, FrenchSuit.HEART), card(FrenchRank.THREE, FrenchSuit.HEART),
                        card(FrenchRank.FOUR, FrenchSuit.HEART), card(FrenchRank.FIVE, FrenchSuit.HEART),
                        card(FrenchRank.SIX, FrenchSuit.HEART), card(FrenchRank.SEVEN, FrenchSuit.HEART),
                        card(FrenchRank.EIGHT, FrenchSuit.HEART), card(FrenchRank.NINE, FrenchSuit.HEART),
                        card(FrenchRank.TEN, FrenchSuit.HEART))
                .build();

        assertThat(rankStrategy(0.5).decide(obs, memory()), is(new BotDecision.Discard(List.of(
                card(FrenchRank.EIGHT, FrenchSuit.HEART)))));
        assertThat(rankStrategy(0.8).decide(obs, memory()), is(new BotDecision.Discard(List.of(
                card(FrenchRank.EIGHT, FrenchSuit.HEART), card(FrenchRank.FIVE, FrenchSuit.HEART)))));
        assertThat(rankStrategy(0.97).decide(obs, memory()), is(new BotDecision.Discard(List.of(
                card(FrenchRank.EIGHT, FrenchSuit.HEART), card(FrenchRank.FIVE, FrenchSuit.HEART),
                card(FrenchRank.TWO, FrenchSuit.HEART)))));
    }

    @Test
    void givenLieLargerThanTheHand_thenItNeverEmptiesTheHand() {
        BotObservation obs = anObservation()
                .hand(card(FrenchRank.KING, FrenchSuit.HEART), card(FrenchRank.QUEEN, FrenchSuit.HEART))
                .build();

        assertThat(((BotDecision.Discard) rankStrategy(0.97).decide(obs, memory())).cards().size(), is(1));
    }

    @Test
    void givenAHandThatMatchesTheTargetEntirely_thenItWinsByPlayingItWholeEvenIfAClaimCouldBeChallenged() {
        BotObservation obs = anObservation()
                .hand(card(FrenchRank.TWO, FrenchSuit.HEART), card(FrenchRank.TWO, FrenchSuit.SPADE))
                .target(new RankTarget(FrenchRank.TWO))
                .claim(1, ACE, 1)
                .build();

        // 0.0 would make the bot challenge if it considered it
        assertThat(rankStrategy(0.0).decide(obs, memory()), is(new BotDecision.Discard(obs.hand())));
    }

    @Test
    void givenDecliningWouldHandTheWinToTheClaimant_thenItDoesNotPlayItsMatchingHandInstead() {
        BotObservation obs = anObservation()
                .hand(card(FrenchRank.TWO, FrenchSuit.HEART))
                .target(new RankTarget(FrenchRank.TWO))
                .handCount(1, 0)
                .claim(1, ACE, 1)
                .pendingWinner(1)
                .build();

        assertThat(rankStrategy(0.99).decide(obs, memory()), instanceOf(BotDecision.CallBullshit.class));
    }

    @Test
    void givenOneUnplayableCardAndAClaimToChallenge_thenItChallengesRatherThanLieAwayItsLastCard() {
        BotObservation obs = anObservation()
                .hand(card(FrenchRank.KING, FrenchSuit.HEART))
                .claim(1, ACE, 1)
                .build();

        assertThat(rankStrategy(0.99).decide(obs, memory()), instanceOf(BotDecision.CallBullshit.class));
    }

    @Test
    void givenASmallHandWithoutAMatch_thenItLiesAwayTheCardsOutsideTheOneThatWillFitItsLastTurn() {
        // Three seats, target ACE: the KING lands on the bot's own turn 4 turns on, the TWO and FIVE much
        // later. Two cards to get rid of before the KING is due, so the lie sheds the latest one.
        BotObservation obs = anObservation()
                .hand(card(FrenchRank.TWO, FrenchSuit.HEART), card(FrenchRank.FIVE, FrenchSuit.HEART),
                        card(FrenchRank.KING, FrenchSuit.HEART))
                .build();

        assertThat(rankStrategy(0.5).decide(obs, memory()), is(new BotDecision.Discard(List.of(
                card(FrenchRank.FIVE, FrenchSuit.HEART)))));
    }

    @Test
    void givenSuitMode_thenMatchingAndLatestNeededUseSuits() {
        ClaimMode suitMode = new CyclingSuitClaimMode();
        BotObservation lie = anObservation()
                .target(new SuitTarget(FrenchSuit.HEART))
                .hand(card(FrenchRank.TWO, FrenchSuit.DIAMOND), card(FrenchRank.NINE, FrenchSuit.CLUB),
                        card(FrenchRank.KING, FrenchSuit.SPADE))
                .build();
        BotObservation honest = anObservation()
                .target(new SuitTarget(FrenchSuit.HEART))
                .hand(card(FrenchRank.TWO, FrenchSuit.HEART), card(FrenchRank.NINE, FrenchSuit.HEART),
                        card(FrenchRank.KING, FrenchSuit.SPADE))
                .build();

        // three seats: the diamond is due on the bot's third turn, the club on its second, the spade on its first;
        // two cards must go before the diamond comes round, so the lie sheds the latest of the other two
        assertThat(new ProbabilisticBotStrategy(suitMode, new ScriptedRandom(0.5), BotTuning.DEFAULT).decide(lie, memory()),
                is(new BotDecision.Discard(List.of(card(FrenchRank.NINE, FrenchSuit.CLUB)))));
        assertThat(new ProbabilisticBotStrategy(suitMode, new ScriptedRandom(0.5), BotTuning.DEFAULT).decide(honest, memory()),
                is(new BotDecision.Discard(List.of(
                        card(FrenchRank.TWO, FrenchSuit.HEART), card(FrenchRank.NINE, FrenchSuit.HEART)))));
    }

    @Test
    void givenMoreThanFourMatchingCards_thenPlaysFour() {
        List<Card> hearts = new ArrayList<>();
        for (FrenchRank rank : List.of(FrenchRank.TWO, FrenchRank.THREE, FrenchRank.FOUR, FrenchRank.FIVE, FrenchRank.SIX)) {
            hearts.add(card(rank, FrenchSuit.HEART));
        }
        hearts.add(card(FrenchRank.KING, FrenchSuit.SPADE));
        BotObservation obs = anObservation().target(new SuitTarget(FrenchSuit.HEART)).hand(hearts.toArray(Card[]::new)).build();

        BotDecision decision = new ProbabilisticBotStrategy(new CyclingSuitClaimMode(), new ScriptedRandom(0.5), BotTuning.DEFAULT)
                .decide(obs, memory());

        assertThat(((BotDecision.Discard) decision).cards().size(), is(4));
    }

    @Test
    void givenEqualSeeds_thenEqualDecisionsOverManyObservations() {
        ProbabilisticBotStrategy first = new ProbabilisticBotStrategy(new AscendingRankClaimMode(), new Random(99), BotTuning.DEFAULT);
        ProbabilisticBotStrategy second = new ProbabilisticBotStrategy(new AscendingRankClaimMode(), new Random(99), BotTuning.DEFAULT);

        for (int i = 0; i < 200; i++) {
            Bullshit game = new Bullshit(GameId.generate(), 4);
            BotObservation obs = BotObservation.forSeat(game, ME);

            assertThat(first.decide(obs, memory()), is(second.decide(obs, memory())));
        }
    }

    @Test
    void givenCertainLie_thenChallengeRateMatchesTheCap() {
        double rate = challengeRate(impossibleClaim(2));

        assertThat(rate, greaterThanOrEqualTo(0.27));
        assertThat(rate, lessThanOrEqualTo(0.33));
    }

    @Test
    void givenAlmostCertainTruth_thenChallengeRateMatchesTheFloor() {
        double rate = challengeRate(almostCertainTruth());

        assertThat(rate, greaterThanOrEqualTo(0.03));
        assertThat(rate, lessThanOrEqualTo(0.20));
    }

    private static double challengeRate(BotObservation obs) {
        ProbabilisticBotStrategy strategy = new ProbabilisticBotStrategy(
                new AscendingRankClaimMode(), new Random(2026), BotTuning.DEFAULT);
        int calls = 0;
        for (int i = 0; i < 2000; i++) {
            if (strategy.decide(obs, memory()) instanceof BotDecision.CallBullshit) {
                calls++;
            }
        }
        return calls / 2000.0;
    }

    @Test
    void givenBotsPlayingWholeGames_thenEveryDecisionIsAcceptedByTheRules() throws Exception {
        int finished = 0;
        for (ClaimMode mode : List.of(new AscendingRankClaimMode(), new CyclingSuitClaimMode())) {
            for (int seed = 0; seed < 25; seed++) {
                Random random = new Random(seed);
                int seats = 2 + seed % 4;
                Bullshit game = new Bullshit(GameId.generate(), seats, mode);
                List<BotMemory> memories = new ArrayList<>();
                List<ProbabilisticBotStrategy> strategies = new ArrayList<>();
                for (int s = 0; s < seats; s++) {
                    memories.add(new BotMemory(new PlayerId(s)));
                    strategies.add(new ProbabilisticBotStrategy(mode, random, BotTuning.DEFAULT));
                }

                // Any move the rules refuse would throw here; the step cap only bounds long games.
                for (int steps = 0; !game.isFinished() && steps < 5_000; steps++) {
                    play(game, strategies, memories);
                }
                if (game.isFinished()) {
                    finished++;
                }
            }
        }

        assertThat("bots should be able to finish games", finished >= 10, is(true));
    }

    private static void play(Bullshit game, List<ProbabilisticBotStrategy> strategies, List<BotMemory> memories)
            throws Exception {
        int current = game.getCurrentPlayerIndex();
        PlayerId currentId = game.getCurrentPlayer().id();
        if (game.getLastDiscard().isPresent()) {
            for (PlayerId seat : game.getPlayerIds()) {
                if (seat.equals(currentId) || seat.equals(game.getLastDiscard().get().claimant())) {
                    continue;
                }
                BotObservation obs = BotObservation.forSeat(game, seat);
                if (strategies.get(seat.id()).decide(obs, memories.get(seat.id())) instanceof BotDecision.CallBullshit) {
                    game.callBullshit(seat);
                    return;
                }
            }
        }
        BotObservation obs = BotObservation.forSeat(game, currentId);
        BotDecision decision = strategies.get(currentId.id()).decide(obs, memories.get(currentId.id()));
        if (decision instanceof BotDecision.CallBullshit) {
            game.callBullshit(currentId);
        } else if (decision instanceof BotDecision.Discard discard) {
            assertThat(discard.cards().size(), greaterThanOrEqualTo(1));
            assertThat(discard.cards().size(), lessThanOrEqualTo(4));
            game.discard(currentId, discard.cards());
        } else {
            throw new AssertionError("The player on turn must act, seat " + current);
        }
    }
}
