package org.kevinkib.cardgames.mangola.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.mangola.domain.MangolaBoard;
import org.kevinkib.cardgames.mangola.domain.MangolaGeometry;
import org.kevinkib.cardgames.mangola.domain.MangolaPosition;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.MangolaSetup;
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
            MangolaBoard board = MangolaSetup.random(seeds);
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
        // a1 and h1 hold 2 each on an otherwise empty board: find boards where some pit is endless.
        Random random = new Random(7);
        RandomMangolaBot bot = new RandomMangolaBot(random);
        int checkedWithEndless = 0;
        for (int game = 0; game < 400; game++) {
            MangolaBoard board = MangolaSetup.random(random);
            boolean someEndless = MangolaGeometry.loop(Side.A).stream()
                    .filter(p -> board.seeds(p) >= 2)
                    .anyMatch(p -> Sowing.sow(board, Side.A, p, OWNER).endless());
            if (!someEndless || MangolaMoves.legalPits(board, Side.A, OWNER).isEmpty()) {
                continue;
            }
            checkedWithEndless++;
            for (int i = 0; i < 5; i++) {
                int pit = bot.decideMove(BotFixtures.position(board, Side.A), Side.A);
                assertThat(Sowing.sow(board, Side.A, pit, OWNER).endless(), is(false));
            }
        }
        assertThat("the fixture boards must exercise the endless case", checkedWithEndless > 0, is(true));
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
