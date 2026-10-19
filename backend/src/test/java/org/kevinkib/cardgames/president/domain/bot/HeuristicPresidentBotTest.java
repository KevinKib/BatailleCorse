package org.kevinkib.cardgames.president.domain.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cards.domain.Card;

import java.util.List;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;
import static org.kevinkib.cardgames.president.domain.bot.PresidentBotFixtures.atTheBarrier;
import static org.kevinkib.cardgames.president.domain.bot.PresidentBotFixtures.following;
import static org.kevinkib.cardgames.president.domain.bot.PresidentBotFixtures.givingBack;
import static org.kevinkib.cardgames.president.domain.bot.PresidentBotFixtures.leading;
import static org.kevinkib.cardgames.president.domain.bot.PresidentBotFixtures.ranks;

/**
 * One row per heuristic rule of the spec (section 10). The tuning is explicit and deterministic (no random
 * play, no random pass) so a row pins the rule, not a level; the levels are pinned in their own tests.
 */
class HeuristicPresidentBotTest {

    /** Medium's counting and control, without any randomness. */
    private static final PresidentBotTuning CAUTIOUS = new PresidentBotTuning(0, 0, 3, 2, 4, false);
    private static final PresidentBotTuning CAUTIOUS_PLANNER = new PresidentBotTuning(0, 0, 13, 2, 4, true);
    /** A random generator that always draws zero: below every probability, and index zero. */
    private static final RandomGenerator ALWAYS_LOW = () -> 0L;
    /** One that always draws just under one: above every probability. */
    private static final RandomGenerator ALWAYS_HIGH = () -> -1L;

    private static PresidentBotDecision decide(PresidentBotTuning tuning, PresidentBotView view,
                                               PresidentBotMemory memory) {
        return new HeuristicPresidentBot(tuning, ALWAYS_HIGH).decide(view, memory);
    }

    private static String shown(PresidentBotDecision decision) {
        if (decision instanceof PresidentBotDecision.Play play) {
            return ranks(play.cards());
        }
        if (decision instanceof PresidentBotDecision.GiveBack giveBack) {
            return ranks(giveBack.cards());
        }
        return decision instanceof PresidentBotDecision.Pass ? "pass" : "ready";
    }

    // ----- lead the lowest set that does not break a bigger set -----

    static Stream<Arguments> leads() {
        return Stream.of(
                Arguments.of("the lowest single", "3H 7C KH", "3"),
                Arguments.of("the lowest pair, as a pair, not its first card", "6H 6D 9C", "6 6"),
                Arguments.of("a triple is led whole", "4H 4D 4C 9D", "4 4 4"),
                Arguments.of("the lowest set, whatever its size", "5H 8D 8C JH", "5"),
                Arguments.of("a low pair before a higher single", "4H 4D 9C", "4 4"),
                Arguments.of("control cards wait: a five before the twos", "5H 2C 2D", "5"),
                Arguments.of("aces wait too", "9H AC AD", "9"),
                Arguments.of("only control cards left: the lowest of them", "AH 2C", "A"),
                Arguments.of("a king pair is not a control card", "KH KD AC", "K K"));
    }

    @ParameterizedTest(name = "{0}: {1} leads {2}")
    @MethodSource("leads")
    void itLeadsTheLowestUnbrokenNonControlSet(String rule, String hand, String expected) {
        PresidentBotDecision decision = decide(CAUTIOUS, leading(hand), new PresidentBotMemory(PresidentBotFixtures.BOT_LEADING));

        assertThat(shown(decision), is(expected));
    }

    // ----- follow with the lowest beating play that does not break a set; pass rather than break a pair -----

    static Stream<Arguments> follows() {
        return Stream.of(
                Arguments.of("the lowest unbroken beating single", "6H 6D 7C 9D JH", "5H", "7"),
                Arguments.of("a pair answers a pair", "7H 7D 9C JH JD", "5H 5D", "7 7"),
                Arguments.of("nothing beats it: pass", "3H 4D 5C 6H 7D", "9H", "pass"),
                Arguments.of("pass rather than break a pair (long hand)", "6H 6D 8C 8D JH JD", "5H", "pass"),
                Arguments.of("a pair is broken when the hand is short", "6H 6D 8C 8D", "5H", "6"),
                Arguments.of("a triple is not broken for a pair (long hand)", "7H 7D 7C 9D JH QH", "5H 5D", "pass"),
                Arguments.of("control cards are kept (long hand)", "2C 4D 4H 5C 5D 6H", "9H", "pass"),
                Arguments.of("an ace is kept too (long hand)", "AC 4D 4H 5C 5D 6H", "9H", "pass"),
                Arguments.of("a control card is spent when the hand is short", "2C 3D", "9H", "2"),
                Arguments.of("going out on the control card is a forbidden finish", "2C", "9H", "pass"),
                Arguments.of("a control pair that is the whole hand is a forbidden finish", "2C 2D", "9H 9D", "pass"),
                Arguments.of("a control pair is spent when the hand is short", "2C 2D 5D", "9H 9D", "2 2"));
    }

    @ParameterizedTest(name = "{0}: {1} on {2} gives {3}")
    @MethodSource("follows")
    void itFollowsWithTheLowestUnbrokenBeatingPlayOrPasses(String rule, String hand, String top, String expected) {
        PresidentBotDecision decision = decide(CAUTIOUS, following(hand, top), new PresidentBotMemory(PresidentBotFixtures.BOT_FOLLOWING));

        assertThat(shown(decision), is(expected));
    }

    // ----- avoid a forbidden finish -----

    static Stream<Arguments> forbiddenFinishes() {
        return Stream.of(
                Arguments.of("passes rather than going out on a two", "2C", "9H", "pass"),
                Arguments.of("a two that leaves a card is fine", "2C 5D", "9H", "2"),
                Arguments.of("a pair of twos that leaves a card is fine", "2C 2D 5D", "9H 9D", "2 2"),
                Arguments.of("a lone two cannot answer a pair", "2C 5D", "9H 9D", "pass"));
    }

    @ParameterizedTest(name = "{0}: {1} on {2} gives {3}")
    @MethodSource("forbiddenFinishes")
    void itDoesNotGoOutOnATwoWhenThatFinishIsForbidden(String rule, String hand, String top, String expected) {
        PresidentBotDecision decision = decide(CAUTIOUS, following(hand, top, PresidentOptions.defaults()),
                new PresidentBotMemory(PresidentBotFixtures.BOT_FOLLOWING));

        assertThat(shown(decision), is(expected));
    }

    // ----- cuts, forbidden finish on a cut, compulsory play (PR V2) -----

    private static final PresidentOptions SINGLE_CUT = PresidentOptions.defaults();
    private static final PresidentOptions MUST_PLAY = PresidentOptions.defaults().withMustPlay(true);

    static Stream<Arguments> cutsAndCompulsoryPlay() {
        return Stream.of(
                Arguments.of("keeps its cutting two rather than spend it on a low pair (long hand)",
                        "2H 6C 7C 8C 9C", "4C 4D", SINGLE_CUT, "pass"),
                Arguments.of("spends the cutting pair when the hand is short", "2H 2D 6C", "4C 4D", SINGLE_CUT, "2 2"),
                Arguments.of("plays an ordinary higher pair before thinking of a cut",
                        "2H 6C 6D 8C 9C", "4C 4D", SINGLE_CUT, "6 6"),
                Arguments.of("never goes out on a cutting two when that finish is forbidden",
                        "2H", "4C 4D", SINGLE_CUT, "pass"),
                Arguments.of("compulsory play: it must play even its control card", "AC 5C 6C 7C 8C", "KC",
                        MUST_PLAY, "A"),
                Arguments.of("compulsory play: a forced forbidden finish is played, there is no pass", "2C", "9H",
                        MUST_PLAY, "2"),
                Arguments.of("compulsory play: it still passes when nothing can play", "3H 4D 5C 6H 7D", "9H",
                        MUST_PLAY, "pass"));
    }

    @ParameterizedTest(name = "{0}: {1} on {2} gives {4}")
    @MethodSource("cutsAndCompulsoryPlay")
    void itKeepsItsCutsAndNeverPassesWhenThePassIsRefused(String rule, String hand, String top,
                                                          PresidentOptions options, String expected) {
        PresidentBotDecision decision = decide(CAUTIOUS, following(hand, top, options),
                new PresidentBotMemory(PresidentBotFixtures.BOT_FOLLOWING));

        assertThat(shown(decision), is(expected));
    }

    @Test
    void theHardPlannerLeadsTheTwosFirstToKeepALowCardForLast() {
        PresidentBotView view = leading("2C 2D 5H", PresidentOptions.defaults());

        assertThat(shown(decide(CAUTIOUS_PLANNER, view, new PresidentBotMemory(PresidentBotFixtures.BOT_LEADING))), is("2 2"));
        assertThat(shown(decide(CAUTIOUS, view, new PresidentBotMemory(PresidentBotFixtures.BOT_LEADING))), is("5"));
    }

    @Test
    void thePlannerNeverLeavesAHandOfTwosOnlyWhenItLeads() {
        PresidentBotView view = leading("AH 2C", PresidentOptions.defaults());

        assertThat(shown(decide(CAUTIOUS_PLANNER, view, new PresidentBotMemory(PresidentBotFixtures.BOT_LEADING))), is("2"));
        assertThat(shown(decide(CAUTIOUS, view, new PresidentBotMemory(PresidentBotFixtures.BOT_LEADING))), is("A"));
    }

    @Test
    void thePlannerCutsWithItsTwoRatherThanStrandItAsTheLastCard() {
        PresidentBotView view = following("5H 2C", "3H", PresidentOptions.defaults());

        assertThat(shown(decide(CAUTIOUS_PLANNER, view, new PresidentBotMemory(PresidentBotFixtures.BOT_FOLLOWING))), is("2"));
    }

    @Test
    void thePlannerPassesRatherThanPlayTheOnlySetThatStrandsItsTwos() {
        PresidentBotView view = following("5H 5D 2C", "3H 3D", PresidentOptions.defaults());

        assertThat(shown(decide(CAUTIOUS_PLANNER, view, new PresidentBotMemory(PresidentBotFixtures.BOT_FOLLOWING))), is("pass"));
    }

    @Test
    void underCompulsoryPlayThePlannerLeadsTwosWhileTwoOtherSetsRemain() {
        PresidentBotView view = leading("2C 5H 9D", PresidentOptions.defaults().withMustPlay(true));

        assertThat(shown(decide(CAUTIOUS_PLANNER, view, new PresidentBotMemory(PresidentBotFixtures.BOT_LEADING))), is("2"));
    }

    @Test
    void aRandomPlayNeverGoesOutOnATwoNorStrandsTheTwosWhenAnotherChoiceExists() {
        PresidentBotTuning random = new PresidentBotTuning(1, 0, 0, 2, 4, true);
        PresidentBotView out = following("2C 2D", "9H", PresidentOptions.defaults());
        PresidentBotView stranding = leading("5H 2C", PresidentOptions.defaults());

        assertThat(shown(new HeuristicPresidentBot(random, ALWAYS_HIGH).decide(out,
                new PresidentBotMemory(PresidentBotFixtures.BOT_FOLLOWING))), is("pass"));
        assertThat(shown(new HeuristicPresidentBot(random, ALWAYS_LOW).decide(stranding,
                new PresidentBotMemory(PresidentBotFixtures.BOT_LEADING))), is("2"));
    }

    @Test
    void thePlannerKeepsLeadingLowFirstWhenSeveralLowSetsRemain() {
        PresidentBotView view = leading("2C 2D 5H 9D", PresidentOptions.defaults());

        assertThat(shown(decide(CAUTIOUS_PLANNER, view, new PresidentBotMemory(PresidentBotFixtures.BOT_LEADING))), is("5"));
    }

    // ----- card counting: a set nobody can beat is kept for last -----

    @Test
    void aSetNobodyCanBeatIsLedLastWhenTheLevelCounts() {
        PresidentBotView view = leading("QH QD KC");
        PresidentBotMemory counting = new PresidentBotMemory(PresidentBotFixtures.BOT_LEADING);
        counting.learnPlayed(cards("KH KD AH AD AC 2H 2D 2C"));
        PresidentBotMemory blind = new PresidentBotMemory(PresidentBotFixtures.BOT_LEADING);

        // no pair above the queens can be formed any more, but an ace still beats the king: lead the king first
        assertThat(shown(decide(CAUTIOUS_PLANNER, view, counting)), is("K"));
        // a level that counts nothing leads the lowest set
        assertThat(shown(decide(new PresidentBotTuning(0, 0, 0, 2, 4, false), view, blind)), is("Q Q"));
    }

    // ----- exchange: give back the lowest cards, never break a pair -----

    static Stream<Arguments> giveBacks() {
        return Stream.of(
                Arguments.of("the lowest singletons", "3H 5D 9C KH AD 2C", 2, "3 5"),
                Arguments.of("a low pair goes whole", "3H 3D 7C KH AD", 2, "3 3"),
                Arguments.of("one card: the pair stays whole", "3H 3D 7C KH AD", 1, "7"),
                Arguments.of("all pairs: the lowest pair is broken when one card is owed", "4H 4D 9C 9D", 1, "4"),
                Arguments.of("a pair then a card out of the next pair", "4H 4D 9C 9D", 3, "4 4 9"),
                Arguments.of("the best cards just received are not given back", "3H 4D 6C 2S AH", 2, "3 4"),
                Arguments.of("a whole hand of singletons", "3H 4D 5C 6S", 2, "3 4"));
    }

    @ParameterizedTest(name = "{0}: {1} gives {2} back: {3}")
    @MethodSource("giveBacks")
    void itGivesBackTheLowestCardsWithoutBreakingAPair(String rule, String hand, int count, String expected) {
        PresidentBotDecision decision = decide(CAUTIOUS, givingBack(hand, count), new PresidentBotMemory(new org.kevinkib.cardgames.game.PlayerId(0)));

        assertThat(decision, instanceOf(PresidentBotDecision.GiveBack.class));
        assertThat(shown(decision), is(expected));
    }

    // ----- the barrier and the levels -----

    @Test
    void itIsReadyAtTheBarrier() {
        PresidentBotDecision decision = decide(CAUTIOUS, atTheBarrier(), new PresidentBotMemory(PresidentBotFixtures.BOT_LEADING));

        assertThat(decision, instanceOf(PresidentBotDecision.Ready.class));
    }

    @Test
    void anEasyBotPassesAtRandomWhenItCouldPlay() {
        PresidentBotView view = following("7H 9D", "5H");

        PresidentBotDecision lucky = new HeuristicPresidentBot(PresidentBotLevel.EASY.tuning(), ALWAYS_LOW)
                .decide(view, new PresidentBotMemory(PresidentBotFixtures.BOT_FOLLOWING));
        PresidentBotDecision unlucky = new HeuristicPresidentBot(PresidentBotLevel.EASY.tuning(), ALWAYS_HIGH)
                .decide(view, new PresidentBotMemory(PresidentBotFixtures.BOT_FOLLOWING));

        assertThat(shown(lucky), is("pass"));
        assertThat(shown(unlucky), is("7"));
    }

    @Test
    void anEasyBotLeadsALegalPlayAtRandomButNeverPassesALead() {
        PresidentBotView view = leading("9H KD");

        PresidentBotDecision decision = new HeuristicPresidentBot(PresidentBotLevel.EASY.tuning(), ALWAYS_LOW)
                .decide(view, new PresidentBotMemory(PresidentBotFixtures.BOT_LEADING));

        assertThat(decision, instanceOf(PresidentBotDecision.Play.class));
        List<Card> played = ((PresidentBotDecision.Play) decision).cards();
        assertThat(view.legalPlays().stream().anyMatch(p -> p.cards().equals(played)), is(true));
    }

    @Test
    void aMediumOrHardBotNeverDrawsAtRandom() {
        PresidentBotView view = following("7H 9D", "5H");

        for (PresidentBotLevel level : List.of(PresidentBotLevel.MEDIUM, PresidentBotLevel.HARD)) {
            PresidentBotDecision decision = new HeuristicPresidentBot(level.tuning(), ALWAYS_LOW)
                    .decide(view, new PresidentBotMemory(PresidentBotFixtures.BOT_FOLLOWING));
            assertThat(level + " plays the lowest card", shown(decision), is("7"));
        }
    }

    @Test
    void mediumAndHardBotsBreakASetRatherThanPass() {
        // self-play showed that passing here loses points: the level tunings treat every hand as short
        PresidentBotView view = following("6H 6D 8C 8D JH JD", "5H");

        for (PresidentBotLevel level : List.of(PresidentBotLevel.MEDIUM, PresidentBotLevel.HARD)) {
            PresidentBotDecision decision = new HeuristicPresidentBot(level.tuning(), ALWAYS_HIGH)
                    .decide(view, new PresidentBotMemory(PresidentBotFixtures.BOT_FOLLOWING));
            assertThat(level + " answers with its lowest card", shown(decision), is("6"));
        }
    }

    @Test
    void itRefusesToDecideWhenTheSeatHasNothingToDo() {
        PresidentBotView view = PresidentBotFixtures.following("7H 9D", "5H");
        PresidentBotView notMyTurn = new PresidentBotView(view.seat(), view.version(), view.round(), view.phase(),
                view.currentSeat(), List.of(), view.hand(), view.handSizes(), view.trick(), List.of(),
                view.finishingOrder(), view.titles(), view.pendingReturns(), view.received(), view.given(),
                view.readySeats(), view.totals(), view.options());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> decide(CAUTIOUS, notMyTurn, new PresidentBotMemory(view.seat())));
    }
}
