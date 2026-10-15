package org.kevinkib.cardgames.mangola.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.mangola.domain.MangolaBoard;
import org.kevinkib.cardgames.mangola.domain.MangolaGeometry;
import org.kevinkib.cardgames.mangola.domain.MangolaPosition;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.MangolaLayouts;
import org.kevinkib.cardgames.mangola.domain.Side;
import org.kevinkib.cardgames.mangola.domain.Sowing;

import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class RandomMangolaBotTest {

    private static final MangolaRules OWNER = MangolaRules.OWNER;

    @Test
    void itAlwaysPlaysALegalPitOnRandomBoards() {
        Random seeds = new Random(42);
        RandomMangolaBot bot = new RandomMangolaBot(seeds);
        for (int game = 0; game < 200; game++) {
            MangolaBoard board = MangolaLayouts.random(seeds);
            for (Side side : Side.values()) {
                MangolaPosition position = BotFixtures.position(board, side);
                List<Integer> legal = MangolaMoves.legalPits(board, side, OWNER);
                if (legal.isEmpty()) {
                    continue;
                }
                int pit = bot.decideMove(position, side);
                assertThat(legal, hasItem(pit));
            }
        }
    }

    @Test
    void itNeverPicksAnEndlessPitWhenEndlessSowingsAreIllegal() {
        // The spec's 13-seed fixture: sowing c1 cycles for ever; e1 and g2 are fine.
        MangolaBoard board = BotFixtures.board(BotFixtures.ENDLESS_SIDE_A + " a4:2");
        assertThat(Sowing.sow(board, Side.A, MangolaGeometry.pit("c1"), OWNER).endless(), is(true));
        RandomMangolaBot bot = new RandomMangolaBot(new Random(7));
        Set<Integer> chosen = new java.util.HashSet<>();
        for (int i = 0; i < 300; i++) {
            chosen.add(bot.decideMove(BotFixtures.position(board, Side.A), Side.A));
        }
        assertThat(chosen, not(hasItem(MangolaGeometry.pit("c1"))));
        assertThat(chosen.size() > 1, is(true));
    }

    @Test
    void underDrawAnEndlessPitIsALegalChoice() {
        MangolaRules draw = OWNER.withEndless(MangolaRules.Endless.DRAW);
        MangolaBoard board = BotFixtures.board(BotFixtures.ENDLESS_SIDE_A + " a4:2");
        RandomMangolaBot bot = new RandomMangolaBot(new Random(7));
        Set<Integer> chosen = new java.util.HashSet<>();
        for (int i = 0; i < 300; i++) {
            chosen.add(bot.decideMove(BotFixtures.position(draw, board, 0), Side.A));
        }
        assertThat(chosen, hasItem(MangolaGeometry.pit("c1")));
    }

    @Test
    void itFailsLoudlyWhenThereIsNoLegalPit() {
        RandomMangolaBot bot = new RandomMangolaBot(new Random(1));
        MangolaBoard stuck = BotFixtures.board("a2:1 b2:1 c3:2");
        boolean refused = false;
        try {
            bot.decideMove(BotFixtures.position(stuck, Side.A), Side.A);
        } catch (IllegalStateException e) {
            refused = true;
        }
        assertThat(refused, is(true));
    }

    @Test
    void itsLayoutsAreLegalAndSumTo32() {
        RandomMangolaBot bot = new RandomMangolaBot(new Random(3));
        for (int i = 0; i < 200; i++) {
            Side side = i % 2 == 0 ? Side.A : Side.B;
            int[] layout = bot.decideLayout(OWNER, side, Set.of());
            assertThat(BotFixtures.isLegalLayout(layout), is(true));
            assertThat(BotFixtures.sum(layout), is(32));
            assertThat(BotFixtures.onlyEndless(OWNER, side, layout), is(false));
        }
    }

    @Test
    void itNeverRepeatsALayoutItWasToldWasRefusedEvenWithAConstantGenerator() {
        RandomMangolaBot bot = new RandomMangolaBot(BotFixtures.CONSTANT);
        Set<List<Integer>> rejected = new java.util.HashSet<>();
        for (int attempt = 0; attempt < 40; attempt++) {
            int[] layout = bot.decideLayout(OWNER, Side.A, rejected);
            assertThat(rejected, not(hasItem(BotFixtures.list(layout))));
            assertThat(BotFixtures.isLegalLayout(layout), is(true));
            rejected.add(BotFixtures.list(layout));
        }
    }
}
