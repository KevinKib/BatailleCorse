package org.kevinkib.cardgames.mangola.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.mangola.domain.MangolaBoard;
import org.kevinkib.cardgames.mangola.domain.MangolaGeometry;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Side;

import java.util.Random;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class GreedyMangolaBotTest {

    private static final MangolaRules OWNER = MangolaRules.OWNER;

    @Test
    void itTakesTheCaptureOfExampleE3OverAQuietMove() {
        // E3: a2:2 c2:1 against c3:2 c4:3 captures 5 seeds; g1:2 is a quiet move that captures nothing.
        MangolaBoard board = BotFixtures.board("a2:2 c2:1 g1:2 c3:2 c4:3");
        GreedyMangolaBot bot = new GreedyMangolaBot(new Random(1));

        int pit = bot.decideMove(BotFixtures.position(board, Side.A), Side.A);

        assertThat(MangolaGeometry.name(pit), is("a2"));
    }

    @Test
    void itPlaysTheBiggestCaptureWhenSeveralPitsCapture() {
        // a2 captures 2 (c3:1, c4:1); e2 is later in the loop and captures 5 (g3:2, g4:3), the E3 shape shifted.
        MangolaBoard board = BotFixtures.board("a2:2 c2:1 c3:1 c4:1 e2:2 g2:1 g3:2 g4:3");
        GreedyMangolaBot bot = new GreedyMangolaBot(new Random(1));

        int pit = bot.decideMove(BotFixtures.position(board, Side.A), Side.A);

        assertThat(MangolaGeometry.name(pit), is("e2"));
    }

    @Test
    void itWorksFromSideBToo() {
        // The mirror of E3 on side B: c3:1 e3:2 -> the sowing of e3 ends on f3... use the plain symmetric case.
        MangolaBoard board = BotFixtures.board("c2:2 c1:3 a3:2 c3:1 g4:2");
        GreedyMangolaBot bot = new GreedyMangolaBot(new Random(1));

        int pit = bot.decideMove(BotFixtures.position(board, Side.B), Side.B);

        // a3:2 drops on b3 and lands on c3 (occupied): c2 and c1 are both occupied, a capture of 5.
        assertThat(MangolaGeometry.name(pit), is("a3"));
    }

    @Test
    void withoutACaptureItKeepsTheMostMovesAvailable() {
        // Two quiet moves, equal in seeds (nothing is captured); the tie is broken on the pits left to sow.
        MangolaBoard board = BotFixtures.board("a2:3 g1:2 h4:2");
        GreedyMangolaBot bot = new GreedyMangolaBot(new Random(1));

        int pit = bot.decideMove(BotFixtures.position(board, Side.A), Side.A);

        assertThat(MangolaMoves.legalPits(board, Side.A, OWNER).contains(pit), is(true));
        MangolaBoard after = MangolaMoves.play(board, Side.A, pit, OWNER).board();
        int best = MangolaMoves.legalPits(board, Side.A, OWNER).stream()
                .mapToInt(p -> MangolaMoves.mobility(MangolaMoves.play(board, Side.A, p, OWNER).board(), Side.A, OWNER))
                .max().orElseThrow();
        assertThat(MangolaMoves.mobility(after, Side.A, OWNER), is(best));
    }

    @Test
    void itNeverPicksAnEndlessPit() {
        Random random = new Random(11);
        GreedyMangolaBot bot = new GreedyMangolaBot(random);
        for (int i = 0; i < 300; i++) {
            MangolaBoard board = org.kevinkib.cardgames.mangola.domain.MangolaLayouts.random(random);
            if (MangolaMoves.legalPits(board, Side.A, OWNER).isEmpty()) {
                continue;
            }
            int pit = bot.decideMove(BotFixtures.position(board, Side.A), Side.A);
            assertThat(MangolaMoves.legalPits(board, Side.A, OWNER).contains(pit), is(true));
        }
    }

    @Test
    void underDrawItAvoidsTheEndlessPitWhileAnotherMoveExists() {
        MangolaRules draw = OWNER.withEndless(MangolaRules.Endless.DRAW);
        MangolaBoard board = BotFixtures.board(BotFixtures.ENDLESS_SIDE_A + " a4:2");
        for (long seed = 0; seed < 30; seed++) {
            int pit = new GreedyMangolaBot(new Random(seed)).decideMove(BotFixtures.position(draw, board, 0), Side.A);
            assertThat(pit == MangolaGeometry.pit("c1"), is(false));
        }
    }

    @Test
    void underDrawItPlaysTheEndlessPitWhenItIsTheOnlyMove() {
        MangolaRules draw = OWNER.withEndless(MangolaRules.Endless.DRAW);
        // Found by a random search (see EndlessSowingTest): the only pit with two seeds, h2, is endless.
        MangolaBoard board = BotFixtures.board("b1:1 d1:1 f1:1 h1:1 a2:1 c2:1 e2:1 g2:1 h2:3 a4:2");
        int pit = new GreedyMangolaBot(new Random(1)).decideMove(BotFixtures.position(draw, board, 0), Side.A);
        assertThat(MangolaGeometry.name(pit), is("h2"));
    }

    @Test
    void itsLayoutsAreLegalAndVaried() {
        GreedyMangolaBot bot = new GreedyMangolaBot(new Random(5));
        Set<java.util.List<Integer>> seen = new java.util.HashSet<>();
        for (int i = 0; i < 60; i++) {
            int[] layout = bot.decideLayout(OWNER, Side.B, Set.of());
            assertThat(BotFixtures.isLegalLayout(layout), is(true));
            assertThat(BotFixtures.onlyEndless(OWNER, Side.B, layout), is(false));
            seen.add(BotFixtures.list(layout));
        }
        assertThat(seen.size() > 5, is(true));
    }

    @Test
    void itIsDeterministicWithAConstantGenerator() {
        MangolaBoard board = BotFixtures.board("a2:2 c2:1 g1:2 c3:2 c4:3");
        int first = new GreedyMangolaBot(BotFixtures.CONSTANT).decideMove(BotFixtures.position(board, Side.A), Side.A);
        int second = new GreedyMangolaBot(BotFixtures.CONSTANT).decideMove(BotFixtures.position(board, Side.A), Side.A);
        assertThat(first, is(second));
    }
}
