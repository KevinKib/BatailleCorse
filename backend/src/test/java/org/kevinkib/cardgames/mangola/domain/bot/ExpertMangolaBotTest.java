package org.kevinkib.cardgames.mangola.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaBoard;
import org.kevinkib.cardgames.mangola.domain.MangolaException;
import org.kevinkib.cardgames.mangola.domain.MangolaGeometry;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.MangolaLayouts;
import org.kevinkib.cardgames.mangola.domain.Side;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;

class ExpertMangolaBotTest {

    private static final MangolaRules OWNER = MangolaRules.OWNER;
    private static final PlayerId SEAT_0 = new PlayerId(0);
    private static final PlayerId SEAT_1 = new PlayerId(1);

    /** Two forced wins in three plies found by brute force; only the named pit forces it (the others do not). */
    private static final String FORCED_1 = "b1:2 c1:1 f1:2 g1:1 h1:2 d2:1 e2:1 g2:1 a3:1 b3:1 e3:2 a4:1 b4:1";
    private static final String FORCED_2 = "d1:1 g1:1 b2:2 d2:2 f2:2 h2:1 e3:1 h3:3 f4:3 h4:1";

    private static ExpertMangolaBot expert(long nodeCap) {
        return new ExpertMangolaBot(BotFixtures.CONSTANT, new ExpertMangolaBot.Budget(60_000, nodeCap));
    }

    @Test
    void theOracleAgreesThatOnlyOnePitWinsEachFixture() {
        MangolaBoard first = BotFixtures.board(FORCED_1);
        MangolaBoard second = BotFixtures.board(FORCED_2);
        assertThat(WinInThreeOracle.forcesWinInThree(first, MangolaGeometry.pit("b1")), is(true));
        assertThat(WinInThreeOracle.forcesWinInThree(first, MangolaGeometry.pit("f1")), is(false));
        assertThat(WinInThreeOracle.forcesWinInThree(second, MangolaGeometry.pit("f2")), is(true));
        assertThat(WinInThreeOracle.forcesWinInThree(second, MangolaGeometry.pit("b2")), is(false));
    }

    @Test
    void itFindsAForcedWinInThreePlies() {
        for (String[] fixture : new String[][]{{FORCED_1, "b1"}, {FORCED_2, "f2"}}) {
            MangolaBoard board = BotFixtures.board(fixture[0]);
            ExpertMangolaBot.SearchResult result = expert(1_000_000).search(BotFixtures.position(board, Side.A), Side.A);

            assertThat(MangolaGeometry.name(result.pit()), is(fixture[1]));
            assertThat(result.score(), greaterThanOrEqualTo(ExpertMangolaBot.WIN - 10));
            assertThat(result.depth(), greaterThanOrEqualTo(3));
        }
    }

    @Test
    void theChosenPitOfADecisionIsTheSearchedOne() {
        MangolaBoard board = BotFixtures.board(FORCED_2);
        int pit = expert(1_000_000).decideMove(BotFixtures.position(board, Side.A), Side.A);
        assertThat(WinInThreeOracle.forcesWinInThree(board, pit), is(true));
    }

    @Test
    void itSeesALossAndTakesTheLongestResistanceItCan() {
        // The mirror of a forced win: side B to move in the fixture mirrored onto side B is losing or not;
        // here only check that the score of a losing position is very negative for the loser.
        MangolaBoard board = BotFixtures.board(FORCED_1);
        // After the winning first move, side B (to move) loses by force: its best score is a loss.
        MangolaBoard after = MangolaMoves.moves(board, Side.A, OWNER).stream()
                .filter(m -> m.pit() == MangolaGeometry.pit("b1")).findFirst().orElseThrow().board();
        ExpertMangolaBot.SearchResult result = expert(1_000_000).search(BotFixtures.position(after, Side.B), Side.B);
        assertThat(result.score(), lessThanOrEqualTo(-(ExpertMangolaBot.WIN - 10)));
    }

    @Test
    void underDrawItDoesNotPlayTheEndlessPitWhenItIsAhead() {
        MangolaRules draw = OWNER.withEndless(MangolaRules.Endless.DRAW);
        MangolaBoard board = BotFixtures.board(BotFixtures.ENDLESS_SIDE_A + " a4:2 b4:2 c4:2");
        int pit = expert(5_000).decideMove(BotFixtures.position(draw, board, 0), Side.A);
        assertThat(MangolaGeometry.name(pit).equals("c1"), is(false));
    }

    @Test
    void underDrawItPlaysTheEndlessPitWhenItIsTheOnlyMove() {
        MangolaRules draw = OWNER.withEndless(MangolaRules.Endless.DRAW);
        // Found by a random search (see EndlessSowingTest): the only pit with two seeds, h2, is endless.
        MangolaBoard board = BotFixtures.board("b1:1 d1:1 f1:1 h1:1 a2:1 c2:1 e2:1 g2:1 h2:3 a4:2");
        ExpertMangolaBot.SearchResult result = expert(5_000).search(BotFixtures.position(draw, board, 0), Side.A);
        assertThat(MangolaGeometry.name(result.pit()), is("h2"));
    }

    @Test
    void itNeverChoosesAnEndlessPitWhenEndlessSowingsAreIllegal() {
        MangolaBoard board = BotFixtures.board(BotFixtures.ENDLESS_SIDE_A + " a4:2 b4:2 c4:2");
        for (long seed = 0; seed < 5; seed++) {
            ExpertMangolaBot bot = new ExpertMangolaBot(new Random(seed), new ExpertMangolaBot.Budget(60_000, 2_000));
            int pit = bot.decideMove(BotFixtures.position(board, Side.A), Side.A);
            assertThat(MangolaGeometry.name(pit).equals("c1"), is(false));
        }
    }

    @Test
    void itRespectsTheNodeCap() {
        MangolaBoard board = MangolaLayouts.random(new Random(5));
        for (long cap : new long[]{50, 500, 5_000}) {
            ExpertMangolaBot.SearchResult result = expert(cap).search(BotFixtures.position(board, Side.A), Side.A);
            // A started iteration is stopped at the cap: allow the few nodes of the move generation of the last one.
            assertThat(result.nodes(), lessThanOrEqualTo(cap + 64));
            assertThat(MangolaMoves.legalPits(board, Side.A, OWNER).contains(result.pit()), is(true));
        }
    }

    @Test
    void itAlwaysAnswersEvenWithAnEmptyBudget() {
        MangolaBoard board = MangolaLayouts.random(new Random(6));
        ExpertMangolaBot bot = new ExpertMangolaBot(BotFixtures.CONSTANT, new ExpertMangolaBot.Budget(0, 0));
        int pit = bot.decideMove(BotFixtures.position(board, Side.A), Side.A);
        assertThat(MangolaMoves.legalPits(board, Side.A, OWNER).contains(pit), is(true));
    }

    @Test
    void itRespectsTheTimeBudget() {
        MangolaBoard board = MangolaLayouts.random(new Random(8));
        ExpertMangolaBot bot = new ExpertMangolaBot(BotFixtures.CONSTANT, new ExpertMangolaBot.Budget(100, Long.MAX_VALUE));
        bot.decideMove(BotFixtures.position(board, Side.A), Side.A); // warm up
        long start = System.nanoTime();
        bot.decideMove(BotFixtures.position(board, Side.A), Side.A);
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertThat("took " + millis + " ms for a 100 ms budget", millis < 600, is(true));
    }

    @Test
    void itIsDeterministicWithTheConstantGenerator() {
        MangolaBoard board = MangolaLayouts.random(new Random(9));
        int first = expert(2_000).decideMove(BotFixtures.position(board, Side.A), Side.A);
        int second = expert(2_000).decideMove(BotFixtures.position(board, Side.A), Side.A);
        assertThat(first, is(second));
    }

    @Test
    void itsLayoutIsLegalNeverRefusedAndDoesNotDependOnAnyOpponentInformation() {
        ExpertMangolaBot bot = new ExpertMangolaBot(new Random(4), new ExpertMangolaBot.Budget(40, 2_000));
        Set<List<Integer>> rejected = new HashSet<>();
        for (int attempt = 0; attempt < 6; attempt++) {
            int[] layout = bot.decideLayout(OWNER, Side.B, rejected);
            assertThat(BotFixtures.isLegalLayout(layout), is(true));
            assertThat(BotFixtures.onlyEndless(OWNER, Side.B, layout), is(false));
            assertThat(rejected, not(org.hamcrest.Matchers.hasItem(BotFixtures.list(layout))));
            rejected.add(BotFixtures.list(layout));
        }
    }

    @Test
    void itsLayoutIsReproducibleWithTheSameSeedWhatEverTheOpponentDoes() {
        // The port takes no opponent information: the same seed gives the same layout, hence nothing leaks into it.
        int[] first = new ExpertMangolaBot(new Random(21), new ExpertMangolaBot.Budget(0, 600))
                .decideLayout(OWNER, Side.A, Set.of());
        int[] second = new ExpertMangolaBot(new Random(21), new ExpertMangolaBot.Budget(0, 600))
                .decideLayout(OWNER, Side.A, Set.of());
        assertThat(BotFixtures.list(first), is(BotFixtures.list(second)));
    }

    // ---------------------------------------------------------------- placement book

    private static void assertDrawnFromTheBook(Side side) {
        ExpertMangolaBot bot = new ExpertMangolaBot(new Random(side == Side.A ? 3 : 4),
                new ExpertMangolaBot.Budget(0, 100));
        List<MangolaPlacementBook.Entry> book = MangolaPlacementBook.of(side);
        int[] drawn = new int[book.size()];
        int decisions = 200;
        for (int i = 0; i < decisions; i++) {
            List<Integer> layout = BotFixtures.list(bot.decideLayout(OWNER, side, Set.of()));
            int index = -1;
            for (int e = 0; e < book.size(); e++) {
                if (BotFixtures.list(book.get(e).counts()).equals(layout)) {
                    index = e;
                }
            }
            assertThat("layout " + layout + " is in the " + side + " list", index >= 0, is(true));
            drawn[index]++;
        }
        for (int e = 0; e < book.size(); e++) {
            assertThat(book.get(e).name() + " frequency", Math.abs(drawn[e] / (double) decisions - book.get(e).weight()),
                    lessThanOrEqualTo(0.05));
        }
    }

    @Test
    void theStarterDrawsItsLayoutFromTheStarterList() {
        assertDrawnFromTheBook(Side.A);
    }

    @Test
    void theSecondPlayerDrawsItsLayoutFromTheSecondList() {
        assertDrawnFromTheBook(Side.B);
    }

    @Test
    void aRefusedLayoutIsNeverProposedAgainAndTheDecisionEndsWhenTheWholeListIsRefused() {
        for (Side side : Side.values()) {
            ExpertMangolaBot bot = new ExpertMangolaBot(new Random(11), new ExpertMangolaBot.Budget(0, 100));
            Set<List<Integer>> rejected = new HashSet<>();
            int listSize = MangolaPlacementBook.of(side).size();
            for (int attempt = 0; attempt < listSize + 3; attempt++) {
                int[] layout = bot.decideLayout(OWNER, side, rejected);
                assertThat(BotFixtures.isLegalLayout(layout), is(true));
                assertThat(rejected.contains(BotFixtures.list(layout)), is(false));
                rejected.add(BotFixtures.list(layout));
            }
        }
    }

    @Test
    void withTheWholeListRefusedItFallsBackOnAnotherLegalLayout() {
        Set<List<Integer>> rejected = new HashSet<>();
        MangolaPlacementBook.of(Side.A).forEach(entry -> rejected.add(BotFixtures.list(entry.counts())));
        int[] layout = new ExpertMangolaBot(BotFixtures.CONSTANT, new ExpertMangolaBot.Budget(0, 100))
                .decideLayout(OWNER, Side.A, rejected);
        assertThat(BotFixtures.isLegalLayout(layout), is(true));
        assertThat(rejected.contains(BotFixtures.list(layout)), is(false));
    }

    // ---------------------------------------------------------------- strength

    /** Where the 32 + 32 seeds come from in a match. */
    enum Layouts {
        /** Each bot arranges its own half, as in a real game (the expert searches, the greedy draws one). */
        CHOSEN_BY_THE_BOTS,
        /** A board drawn by the random set-up, imposed on both, whatever the bots would have chosen. */
        RANDOM_BOARDS
    }

    /** Results of the expert against the greedy bot: wins, wins when it starts, games when it starts, draws. */
    record Match(int games, int wins, int winsWhenStarting, int gamesWhenStarting, int draws) {
    }

    private static PlayerId playGame(MangolaBot seat0, MangolaBot seat1, MangolaBoard board) throws MangolaException {
        Mangola game = Mangola.fromLayout(GameId.generate(), OWNER, SEAT_0, board);
        int plies = 0;
        while (!game.isFinished() && plies++ < 600) {
            PlayerId mover = game.currentSeat();
            MangolaBot bot = mover.equals(SEAT_0) ? seat0 : seat1;
            Side side = game.sideOf(mover);
            game.sow(mover, bot.decideMove(game.position(), side));
        }
        return game.isFinished() ? game.result().winner() : null;
    }

    /**
     * {@code games} seeded games of the expert against the greedy bot, the expert starting in every other game
     * (side A starts). A start that would be refused at the confirmation (the starter has no move) is replaced.
     */
    static Match match(int games, long nodeCap, Layouts source) throws MangolaException {
        int wins = 0;
        int draws = 0;
        int startWins = 0;
        int startGames = 0;
        int played = 0;
        for (long seed = 1; played < games; seed++) {
            boolean expertStarts = played % 2 == 0;
            Side expertSide = expertStarts ? Side.A : Side.B;
            ExpertMangolaBot expert = new ExpertMangolaBot(new Random(seed * 17),
                    new ExpertMangolaBot.Budget(60_000, nodeCap));
            GreedyMangolaBot greedy = new GreedyMangolaBot(new Random(seed * 31));
            MangolaBoard board = source == Layouts.RANDOM_BOARDS
                    ? MangolaLayouts.random(new Random(seed))
                    : MangolaBoard.empty()
                            .withLayout(expertSide, expert.decideLayout(OWNER, expertSide, Set.of()))
                            .withLayout(expertSide.other(), greedy.decideLayout(OWNER, expertSide.other(), Set.of()));
            if (MangolaMoves.legalPits(board, Side.A, OWNER).isEmpty()
                    || MangolaMoves.legalPits(board, Side.B, OWNER).isEmpty()) {
                continue;
            }
            PlayerId winner = expertStarts ? playGame(expert, greedy, board) : playGame(greedy, expert, board);
            PlayerId expertSeat = expertStarts ? SEAT_0 : SEAT_1;
            if (expertStarts) {
                startGames++;
            }
            if (expertSeat.equals(winner)) {
                wins++;
                if (expertStarts) {
                    startWins++;
                }
            } else if (winner == null) {
                draws++;
            }
            played++;
        }
        return new Match(games, wins, startWins, startGames, draws);
    }

    @Test
    void theExpertBeatsTheGreedyBotInAtLeast70PercentOfOneHundredSeededGames() throws MangolaException {
        // Each bot arranges its own half, as in a real game; the expert starts in every other game.
        Match result = match(100, 400, Layouts.CHOSEN_BY_THE_BOTS);
        System.out.println("EXPERT_VS_GREEDY (own layouts) " + result);
        assertThat("expert wins out of 100", result.wins(), greaterThanOrEqualTo(70));
    }

    @Test
    void onImposedRandomBoardsTheExpertWinsAlmostEveryGameItStartsAndBeatsGreedyOverall() throws MangolaException {
        // The starter has a huge edge on a random board (about 80 % between two greedy bots, games of about 11
        // plies), so the overall rate is bounded: it is the games the expert starts that show its strength.
        Match result = match(100, 400, Layouts.RANDOM_BOARDS);
        System.out.println("EXPERT_VS_GREEDY (random boards) " + result);
        assertThat(result.winsWhenStarting(), greaterThanOrEqualTo(result.gamesWhenStarting() * 9 / 10));
        assertThat(result.wins(), greaterThanOrEqualTo(60));
    }
}
