package org.kevinkib.cardgames.kobo.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.power.Power;
import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.ref;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

class HeuristicKoboBotTest {

    private static HeuristicKoboBot bot(KoboBotLevel level) {
        return new HeuristicKoboBot(level.tuning());
    }

    private static KoboBotMemory memory(int seat, KoboBotLevel level) {
        return new KoboBotMemory(seat(seat), level.tuning().followsMoves());
    }

    private static KoboBotDecision decide(KoboBotLevel level, Kobo kobo, KoboBotMemory memory) {
        return bot(level).decide(KoboBotView.forSeat(kobo, memory.seat()), memory);
    }

    private static Optional<KoboBotDecision.Match> matchFor(KoboBotLevel level, Kobo kobo, KoboBotMemory memory) {
        return bot(level).matchFor(KoboBotView.forSeat(kobo, memory.seat()), memory);
    }

    // ------------------------------------------------------------------ the deck average

    @Test
    void anUnknownCardIsWorthTheDeckAverage() {
        assertThat(HeuristicKoboBot.UNKNOWN_VALUE, closeTo(6.5, 1e-9));
    }

    // ------------------------------------------------------------------ barriers and draw

    @Test
    void inMemorising_thenReady() {
        Kobo kobo = aKobo().withPlayers(2).inPhase(Phase.MEMORISING).build();

        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory(1, KoboBotLevel.MEDIUM)),
                is(new KoboBotDecision.Ready()));
    }

    @Test
    void inRoundOver_thenReady() {
        Kobo kobo = aKobo().withPlayers(2).inPhase(Phase.ROUND_OVER).build();

        assertThat(decide(KoboBotLevel.HARD, kobo, memory(0, KoboBotLevel.HARD)), is(new KoboBotDecision.Ready()));
    }

    @Test
    void whenAlreadyReady_thenThereIsNothingToDecide() throws Exception {
        Kobo kobo = aKobo().withPlayers(2).inPhase(Phase.MEMORISING).build();
        kobo.ready(seat(0));

        assertThrows(IllegalStateException.class, () -> decide(KoboBotLevel.EASY, kobo, memory(0, KoboBotLevel.EASY)));
    }

    @Test
    void inDraw_thenDraw() {
        Kobo kobo = aKobo().withPlayers(2).inPhase(Phase.DRAW).build();

        assertThat(decide(KoboBotLevel.EASY, kobo, memory(0, KoboBotLevel.EASY)), is(new KoboBotDecision.Draw()));
    }

    @Test
    void whenItIsNotTheBotsTurn_thenThereIsNothingToDecide() {
        Kobo kobo = aKobo().withPlayers(2).inPhase(Phase.DRAW).build();

        assertThrows(IllegalStateException.class, () -> decide(KoboBotLevel.EASY, kobo, memory(1, KoboBotLevel.EASY)));
    }

    // ------------------------------------------------------------------ decision on the drawn card

    private Kobo deciding(String drawn) {
        return aKobo().withTableau("9H", "10H", "7H", "6H").withTableau("5S", "6S", "9D", "KS")
                .inPhase(Phase.DECISION).withDrawn(drawn).build();
    }

    @Test
    void aRedKingIsKeptInPlaceOfTheWorstKnownCard() {
        Kobo kobo = deciding("KH");
        KoboBotMemory memory = memory(0, KoboBotLevel.MEDIUM);
        memory.learn(ref(0, 0), card("9H"), 0);
        memory.learn(ref(0, 1), card("10H"), 0);

        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory), is(new KoboBotDecision.SwapDrawn(1)));
    }

    @Test
    void aSixIsDiscardedWhenEveryOwnCardIsUnknown() {
        Kobo kobo = deciding("6C");

        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory(0, KoboBotLevel.MEDIUM)),
                is(new KoboBotDecision.DiscardDrawn()));
    }

    @Test
    void aLowCardReplacesAnUnknownCardWhenItGainsAtLeastOnePoint() {
        Kobo kobo = deciding("2C");

        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory(0, KoboBotLevel.MEDIUM)),
                is(new KoboBotDecision.SwapDrawn(0)));
    }

    @Test
    void theBiggestCertainOrExpectedGainWins() {
        Kobo kobo = deciding("2C");
        KoboBotMemory memory = memory(0, KoboBotLevel.MEDIUM);
        memory.learn(ref(0, 3), card("6H"), 0);
        memory.learn(ref(0, 2), card("7H"), 0);

        // slot 2 (a known 7, gain 5) beats the unknown slots (expected 6.5, gain 4.5) and the known 6 (gain 4)
        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory), is(new KoboBotDecision.SwapDrawn(2)));
    }

    @Test
    void aSevenIsDiscardedForItsPowerWhenNothingInHandIsWorse() {
        Kobo kobo = deciding("7S");
        KoboBotMemory memory = memory(0, KoboBotLevel.HARD);
        memory.learn(ref(0, 0), card("AH"), 0);
        memory.learn(ref(0, 1), card("2H"), 0);
        memory.learn(ref(0, 2), card("3H"), 0);
        memory.learn(ref(0, 3), card("4H"), 0);

        assertThat(decide(KoboBotLevel.HARD, kobo, memory), is(new KoboBotDecision.DiscardDrawn()));
    }

    // ------------------------------------------------------------------ powers

    private Kobo power(Power power) {
        return aKobo().withTableau("9H", "10H", "7H", "6H").withTableau("5S", "6S", "9D", "KS")
                .withTableau("AC", "2C", "3C", "10S")
                .inPhase(Phase.POWER).withPendingPower(power).build();
    }

    @Test
    void peekOwnLooksAtTheLowestUnknownOwnSlot() {
        Kobo kobo = power(Power.PEEK_OWN);
        KoboBotMemory memory = memory(0, KoboBotLevel.MEDIUM);
        memory.learn(ref(0, 0), card("9H"), 0);
        memory.learn(ref(0, 1), card("10H"), 0);

        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory), is(new KoboBotDecision.Peek(ref(0, 2))));
    }

    @Test
    void peekOwnSkipsWhenEveryOwnCardIsKnown() {
        Kobo kobo = power(Power.PEEK_OWN);
        KoboBotMemory memory = memory(0, KoboBotLevel.MEDIUM);
        memory.learn(ref(0, 0), card("9H"), 0);
        memory.learn(ref(0, 1), card("10H"), 0);
        memory.learn(ref(0, 2), card("7H"), 0);
        memory.learn(ref(0, 3), card("6H"), 0);

        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory), is(new KoboBotDecision.SkipPower()));
    }

    @Test
    void peekOtherLooksAtAnUnknownCardOfTheLowestEstimateOpponent() {
        Kobo kobo = power(Power.PEEK_OTHER);
        KoboBotMemory memory = memory(0, KoboBotLevel.MEDIUM);
        memory.learn(ref(2, 0), card("AC"), 0);
        memory.learn(ref(2, 1), card("2C"), 0);

        // seat 2 estimate 1 + 2 + 6.5 + 6.5 = 16, seat 1 estimate 26
        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory), is(new KoboBotDecision.Peek(ref(2, 2))));
    }

    @Test
    void peekOtherSkipsWhenEveryOpponentCardIsKnown() {
        Kobo kobo = aKobo().withTableau("9H", "10H", "7H", "6H").withTableau("5S", "6S", "9D", "KS")
                .inPhase(Phase.POWER).withPendingPower(Power.PEEK_OTHER).build();
        KoboBotMemory memory = memory(0, KoboBotLevel.MEDIUM);
        memory.learn(ref(1, 0), card("5S"), 0);
        memory.learn(ref(1, 1), card("6S"), 0);
        memory.learn(ref(1, 2), card("9D"), 0);
        memory.learn(ref(1, 3), card("KS"), 0);

        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory), is(new KoboBotDecision.SkipPower()));
    }

    @Test
    void aJackSwapsAKnownTwelveForAnUnknownOpponentCard() {
        Kobo kobo = aKobo().withTableau("QH", "10H", "7H", "6H").withTableau("5S", "6S", "9D", "KS")
                .inPhase(Phase.POWER).withPendingPower(Power.BLIND_SWAP).build();
        KoboBotMemory memory = memory(0, KoboBotLevel.MEDIUM);
        memory.learn(ref(0, 0), card("QH"), 0);

        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory), is(new KoboBotDecision.BlindSwap(0, ref(1, 0))));
    }

    @Test
    void aJackTakesTheLowestKnownOpponentCardFirst() {
        Kobo kobo = aKobo().withTableau("QH", "10H", "7H", "6H").withTableau("5S", "6S", "9D", "KS")
                .inPhase(Phase.POWER).withPendingPower(Power.BLIND_SWAP).build();
        KoboBotMemory memory = memory(0, KoboBotLevel.MEDIUM);
        memory.learn(ref(0, 0), card("QH"), 0);
        memory.learn(ref(1, 3), card("KS"), 0);
        memory.learn(ref(1, 1), card("2S"), 0);

        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory), is(new KoboBotDecision.BlindSwap(0, ref(1, 1))));
    }

    @Test
    void aJackSkipsWhenNothingIsKnown() {
        Kobo kobo = aKobo().withTableau("QH", "10H", "7H", "6H").withTableau("5S", "6S", "9D", "KS")
                .inPhase(Phase.POWER).withPendingPower(Power.BLIND_SWAP).build();

        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory(0, KoboBotLevel.MEDIUM)),
                is(new KoboBotDecision.SkipPower()));
    }

    @Test
    void aKingPeeksAnOpponentCardKnownToBeWorthThreeOrLess() {
        Kobo kobo = aKobo().withTableau("QH", "10H", "7H", "6H").withTableau("5S", "6S", "2D", "KS")
                .inPhase(Phase.POWER).withPendingPower(Power.KING).build();
        KoboBotMemory memory = memory(0, KoboBotLevel.HARD);
        memory.learn(ref(1, 2), card("2D"), 0);

        assertThat(decide(KoboBotLevel.HARD, kobo, memory), is(new KoboBotDecision.Peek(ref(1, 2))));
    }

    @Test
    void aKingPeeksAnUnknownCardOfTheLowestEstimateOpponentOtherwise() {
        Kobo kobo = aKobo().withTableau("QH", "10H", "7H", "6H").withTableau("5S", "6S", "9D", "KS")
                .inPhase(Phase.POWER).withPendingPower(Power.KING).build();
        KoboBotMemory memory = memory(0, KoboBotLevel.HARD);
        memory.learn(ref(1, 0), card("9S"), 0);

        assertThat(decide(KoboBotLevel.HARD, kobo, memory), is(new KoboBotDecision.Peek(ref(1, 1))));
    }

    private Kobo kingDecision(String peeked) throws Exception {
        Kobo kobo = aKobo().withTableau("AH", "2H", "3H", "10S").withTableau(peeked, "6S", "9D", "KS")
                .inPhase(Phase.POWER).withPendingPower(Power.KING).build();
        kobo.peek(seat(0), ref(1, 0));
        return kobo;
    }

    @Test
    void aKingSwapsAPeekedOneForTheKnownTen() throws Exception {
        Kobo kobo = kingDecision("AS");
        KoboBotMemory memory = memory(0, KoboBotLevel.MEDIUM);
        memory.learn(ref(0, 3), card("10S"), 0);

        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory), is(new KoboBotDecision.KingSwap(3)));
    }

    @Test
    void aKingSkipsWhenThePeekedCardGainsLessThanTwoPoints() throws Exception {
        Kobo kobo = kingDecision("9S");
        KoboBotMemory memory = memory(0, KoboBotLevel.MEDIUM);
        memory.learn(ref(0, 3), card("10S"), 0);

        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory), is(new KoboBotDecision.SkipPower()));
    }

    // ------------------------------------------------------------------ announcing Kobo

    private Kobo turnEnd(String... own) {
        return aKobo().withTableau(own).withTableau("5S", "6S", "9D", "KS").inPhase(Phase.TURN_END).build();
    }

    private static void knowAll(KoboBotMemory memory, String... codes) {
        for (int i = 0; i < codes.length; i++) {
            memory.learn(ref(0, i), card(codes[i]), 0);
        }
    }

    @Test
    void hardAnnouncesAtAnEstimateOfFiveWithEveryCardKnown() {
        Kobo kobo = turnEnd("AH", "2H", "AS", "KH");
        KoboBotMemory memory = memory(0, KoboBotLevel.HARD);
        knowAll(memory, "AH", "2H", "AS", "KH");

        assertThat(decide(KoboBotLevel.HARD, kobo, memory), is(new KoboBotDecision.AnnounceKobo()));
    }

    @Test
    void mediumDoesNotAnnounceWithTwoUnknownCards() {
        Kobo kobo = turnEnd("AH", "2H", "AS", "KH");
        KoboBotMemory memory = memory(0, KoboBotLevel.MEDIUM);
        knowAll(memory, "AH", "2H");

        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory), is(new KoboBotDecision.EndTurn()));
    }

    @Test
    void easyAnnouncesWithTwoUnknownCardsWhenTheEstimateIsLowEnough() {
        Kobo kobo = turnEnd("AH", "AS", "AC", "KH");
        KoboBotMemory memory = memory(0, KoboBotLevel.EASY);
        memory.learn(ref(0, 0), card("AH"), 0);
        memory.learn(ref(0, 3), card("KH"), 0);

        // estimate 1 + 0 + 2 * 6.5 = 14 is above 9: no
        assertThat(decide(KoboBotLevel.EASY, kobo, memory), is(new KoboBotDecision.EndTurn()));
        KoboBotMemory fuller = memory(0, KoboBotLevel.EASY);
        knowAll(fuller, "AH", "AS", "AC");
        // one unknown card: 3 + 6.5 = 9.5 is above 9 too
        assertThat(decide(KoboBotLevel.EASY, kobo, fuller), is(new KoboBotDecision.EndTurn()));
        KoboBotMemory all = memory(0, KoboBotLevel.EASY);
        knowAll(all, "AH", "AS", "AC", "KH");
        assertThat(decide(KoboBotLevel.EASY, kobo, all), is(new KoboBotDecision.AnnounceKobo()));
    }

    @Test
    void doesNotAnnounceWhenAnOpponentsKnownHandIsLower() {
        Kobo kobo = turnEnd("AH", "2H", "AS", "KH");
        KoboBotMemory memory = memory(0, KoboBotLevel.HARD);
        knowAll(memory, "AH", "2H", "AS", "KH");
        memory.learn(ref(1, 0), card("KD"), 0);
        memory.learn(ref(1, 1), card("KD"), 0);
        memory.learn(ref(1, 2), card("AD"), 0);
        memory.learn(ref(1, 3), card("2D"), 0);

        // own estimate 4, opponent 3: needs to be at least 2 lower
        assertThat(decide(KoboBotLevel.HARD, kobo, memory), is(new KoboBotDecision.EndTurn()));
    }

    @Test
    void easyIgnoresTheOpponentsEstimate() {
        Kobo kobo = turnEnd("AH", "2H", "AS", "KH");
        KoboBotMemory memory = memory(0, KoboBotLevel.EASY);
        knowAll(memory, "AH", "2H", "AS", "KH");
        memory.learn(ref(1, 0), card("KD"), 0);
        memory.learn(ref(1, 1), card("KD"), 0);
        memory.learn(ref(1, 2), card("AD"), 0);
        memory.learn(ref(1, 3), card("2D"), 0);

        assertThat(decide(KoboBotLevel.EASY, kobo, memory), is(new KoboBotDecision.AnnounceKobo()));
    }

    @Test
    void alwaysAnnouncesWithNoCardLeft() {
        Kobo kobo = turnEnd();
        KoboBotMemory memory = memory(0, KoboBotLevel.HARD);
        memory.learn(ref(1, 0), card("KD"), 0);
        memory.learn(ref(1, 1), card("KD"), 0);
        memory.learn(ref(1, 2), card("KH"), 0);
        memory.learn(ref(1, 3), card("KH"), 0);

        assertThat(decide(KoboBotLevel.HARD, kobo, memory), is(new KoboBotDecision.AnnounceKobo()));
    }

    @Test
    void theLongerTheRoundTheBolderTheBot() {
        Kobo kobo = turnEnd("AH", "2H", "3H", "AC");
        KoboBotMemory memory = memory(0, KoboBotLevel.MEDIUM);
        knowAll(memory, "AH", "2H", "3H", "AC");
        ScriptedRandom none = new ScriptedRandom();
        for (int i = 0; i < 5; i++) {
            memory.onOwnTurnEnded(none, 0);
        }

        // sixth turn: estimate 7 is above the medium threshold of 6
        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory), is(new KoboBotDecision.EndTurn()));

        memory.onOwnTurnEnded(none, 0);
        // seventh turn: the threshold grew by one
        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory), is(new KoboBotDecision.AnnounceKobo()));
    }

    // ------------------------------------------------------------------ the gift

    /** Seat 0 announces and wins the round; the other seats have the given hands and totals. */
    private Kobo gift(int[] totals, String[]... hands) throws Exception {
        var builder = aKobo().inPhase(Phase.TURN_END).withDrawPile("2C", "3C", "4C", "5C");
        for (String[] hand : hands) {
            builder.withTableau(hand);
        }
        Kobo kobo = builder.withTotals(totals).build();
        kobo.announceKobo(seat(0));
        for (int i = 1; i < hands.length; i++) {
            kobo.draw(seat(i));
            kobo.discardDrawn(seat(i));
            kobo.endTurn(seat(i));
        }
        assertThat(kobo.phase(), is(Phase.GIFT));
        return kobo;
    }

    private static String[] hand(String... codes) {
        return codes;
    }

    @Test
    void theTenPointsPushAnOpponentOverOneHundredWhenTheBotThenLeads() throws Exception {
        Kobo kobo = gift(new int[]{20, 70, 30},
                hand("AH", "2H", "3H", "4H"), hand("5S", "6S", "4D", "7S"), hand("5C", "6C", "4C", "10C"));

        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory(0, KoboBotLevel.MEDIUM)),
                is(new KoboBotDecision.GiveTen(seat(1))));
    }

    @Test
    void theTenPointsAvoidATotalOfSeventyFiveOrOneHundred() throws Exception {
        // provisional totals: seat 1 = 55 + 10 = 65 (+10 would be 75), seat 2 = 48 + 22 = 70
        Kobo kobo = gift(new int[]{90, 55, 48},
                hand("AH", "2H", "3H", "4H"), hand("AS", "2S", "3S", "4S"), hand("5S", "6S", "4D", "7S"));

        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory(0, KoboBotLevel.MEDIUM)),
                is(new KoboBotDecision.GiveTen(seat(2))));
    }

    @Test
    void theTenPointsGoToTheLowestProvisionalTotalOtherwise() throws Exception {
        Kobo kobo = gift(new int[]{90, 30, 48},
                hand("AH", "2H", "3H", "4H"), hand("AS", "2S", "3S", "4S"), hand("5S", "6S", "4D", "7S"));

        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory(0, KoboBotLevel.MEDIUM)),
                is(new KoboBotDecision.GiveTen(seat(1))));
    }

    @Test
    void whenEveryTargetIsBlockedTheFirstOtherSeatGetsTheTen() throws Exception {
        // provisional totals: seat 1 = 55 + 10 = 65, seat 2 = 68 + 22 = 90: both would reset to 50
        Kobo kobo = gift(new int[]{10, 55, 68},
                hand("AH", "2H", "3H", "4H"), hand("AS", "2S", "3S", "4S"), hand("5S", "6S", "4D", "7S"));

        assertThat(decide(KoboBotLevel.MEDIUM, kobo, memory(0, KoboBotLevel.MEDIUM)),
                is(new KoboBotDecision.GiveTen(seat(1))));
    }

    // ------------------------------------------------------------------ the match

    private Kobo live(String discardTop) {
        return aKobo().withTableau("AH", "2H", "3H", "10H").withTableau("5S", "9C", "9D", "KS")
                .withTableau("7C", "8C", "QC", "JC")
                .withDiscard(discardTop).inPhase(Phase.DRAW).build();
    }

    @Test
    void anOwnRememberedCardIsMatchedBeforeAnOpponents() {
        Kobo kobo = live("9S");
        KoboBotMemory memory = memory(0, KoboBotLevel.MEDIUM);
        memory.learn(ref(1, 1), card("9C"), 0);
        memory.learn(ref(0, 2), card("9H"), 0);

        assertThat(matchFor(KoboBotLevel.MEDIUM, kobo, memory).get(),
                is(new KoboBotDecision.Match(ref(0, 2), null, FrenchRank.NINE, 0)));
    }

    @Test
    void anOpponentsRememberedCardIsMatchedAtMediumAndHardGivingTheHighestCard() {
        Kobo kobo = live("9S");
        KoboBotMemory memory = memory(0, KoboBotLevel.HARD);
        memory.learn(ref(1, 1), card("9C"), 0);
        memory.learn(ref(0, 0), card("AH"), 0);
        memory.learn(ref(0, 1), card("2H"), 0);
        memory.learn(ref(0, 2), card("3H"), 0);
        memory.learn(ref(0, 3), card("10H"), 0);

        assertThat(matchFor(KoboBotLevel.HARD, kobo, memory).get(),
                is(new KoboBotDecision.Match(ref(1, 1), 3, FrenchRank.NINE, 0)));
        assertThat(matchFor(KoboBotLevel.MEDIUM, kobo, memory).isPresent(), is(true));
    }

    @Test
    void anUnknownOwnCardIsGivenRatherThanAKnownLowOne() {
        Kobo kobo = live("9S");
        KoboBotMemory memory = memory(0, KoboBotLevel.MEDIUM);
        memory.learn(ref(1, 1), card("9C"), 0);
        memory.learn(ref(0, 0), card("AH"), 0);
        memory.learn(ref(0, 1), card("2H"), 0);
        memory.learn(ref(0, 3), card("3H"), 0);

        assertThat(matchFor(KoboBotLevel.MEDIUM, kobo, memory).get().giveSlot(), is(2));
    }

    @Test
    void theExpectedRevisionIsTheRememberedOne() throws Exception {
        Kobo kobo = aKobo().withTableau("AH", "2H", "9S", "10H").withTableau("5S", "9C", "9D", "KS")
                .inPhase(Phase.DECISION).withDrawn("9H").build();
        kobo.swapDrawn(seat(0), 2);
        KoboBotMemory memory = memory(0, KoboBotLevel.HARD);
        memory.learn(ref(0, 2), card("9H"), 1);

        assertThat(matchFor(KoboBotLevel.HARD, kobo, memory).get(),
                is(new KoboBotDecision.Match(ref(0, 2), null, FrenchRank.NINE, 1)));
    }

    @Test
    void easyNeverMatchesAnOpponentsCard() {
        Kobo kobo = live("9S");
        KoboBotMemory memory = memory(0, KoboBotLevel.EASY);
        memory.learn(ref(1, 1), card("9C"), 0);

        assertThat(matchFor(KoboBotLevel.EASY, kobo, memory).isPresent(), is(false));
    }

    @Test
    void easyStillMatchesItsOwnCard() {
        Kobo kobo = live("9S");
        KoboBotMemory memory = memory(0, KoboBotLevel.EASY);
        memory.learn(ref(0, 2), card("9H"), 0);

        assertThat(matchFor(KoboBotLevel.EASY, kobo, memory).get().target(), is(ref(0, 2)));
    }

    @Test
    void noRememberedCardOfTheRankMeansNoMatch() {
        Kobo kobo = live("9S");
        KoboBotMemory memory = memory(0, KoboBotLevel.HARD);
        memory.learn(ref(0, 0), card("AH"), 0);
        memory.learn(ref(1, 0), card("5S"), 0);

        assertThat(matchFor(KoboBotLevel.HARD, kobo, memory).isPresent(), is(false));
    }

    @Test
    void aBotWithoutCardsGivesNothing() {
        Kobo kobo = aKobo().withTableau().withTableau("5S", "9C", "9D", "KS")
                .withDiscard("9S").inPhase(Phase.DRAW).build();
        KoboBotMemory memory = memory(0, KoboBotLevel.HARD);
        memory.learn(ref(1, 2), card("9D"), 0);

        assertThat(matchFor(KoboBotLevel.HARD, kobo, memory).get(),
                is(new KoboBotDecision.Match(ref(1, 2), null, FrenchRank.NINE, 0)));
    }

    @Test
    void noMatchWithoutADiscardTopOrOutsideALivePhase() {
        KoboBotMemory memory = memory(0, KoboBotLevel.HARD);
        memory.learn(ref(0, 2), card("9H"), 0);
        Kobo noTop = aKobo().withPlayers(2).inPhase(Phase.DRAW).build();
        Kobo memorising = aKobo().withPlayers(2).withDiscard("9S").inPhase(Phase.MEMORISING).build();

        assertThat(matchFor(KoboBotLevel.HARD, noTop, memory).isPresent(), is(false));
        assertThat(matchFor(KoboBotLevel.HARD, memorising, memory).isPresent(), is(false));
    }

    @Test
    void aRememberedCardWhoseSlotChangedIsNotMatched() {
        Kobo kobo = live("9S");
        KoboBotMemory memory = memory(0, KoboBotLevel.HARD);
        memory.learn(ref(0, 2), card("9H"), 5);

        assertThat(matchFor(KoboBotLevel.HARD, kobo, memory).isPresent(), is(false));
    }

    @Test
    void theDecisionTypesAreSealed() {
        assertThat(new KoboBotDecision.Ready(), instanceOf(KoboBotDecision.class));
    }
}
