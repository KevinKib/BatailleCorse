package org.kevinkib.cardgames.mangola.domain.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.kevinkib.cardgames.mangola.domain.MangolaBoard;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.MangolaSetup;
import org.kevinkib.cardgames.mangola.domain.Side;

import java.util.Random;

/**
 * Not a test: a measurement, run on demand with {@code -Dmangola.bench=true -Dtest=ExpertStrengthBenchmark}. It
 * prints the results of the expert against the greedy bot for several node caps and the depth reached under
 * the real time budgets.
 */
@EnabledIfSystemProperty(named = "mangola.bench", matches = "true")
class ExpertStrengthBenchmark {

    @Test
    void winRatesAndDepths() throws Exception {
        for (long cap : new long[]{100, 400, 2_000, 10_000}) {
            for (ExpertMangolaBotTest.Layouts source : ExpertMangolaBotTest.Layouts.values()) {
                long start = System.nanoTime();
                ExpertMangolaBotTest.Match match = ExpertMangolaBotTest.match(100, cap, source);
                System.out.println("BENCH cap=" + cap + " " + source + " " + match + " in "
                        + (System.nanoTime() - start) / 1_000_000 + " ms");
            }
        }
        Random random = new Random(3);
        for (long millis : new long[]{50, 300, 1_000}) {
            ExpertMangolaBot bot = new ExpertMangolaBot(random, new ExpertMangolaBot.Budget(millis, Long.MAX_VALUE));
            long depthSum = 0;
            long nodeSum = 0;
            int n = 20;
            for (int i = 0; i < n; i++) {
                MangolaBoard board = MangolaSetup.random(random);
                if (MangolaMoves.moves(board, Side.A, MangolaRules.OWNER).isEmpty()) {
                    continue;
                }
                ExpertMangolaBot.SearchResult result = bot.search(BotFixtures.position(board, Side.A), Side.A);
                depthSum += result.depth();
                nodeSum += result.nodes();
            }
            System.out.println("BENCH budget=" + millis + "ms meanDepth=" + (double) depthSum / n + " meanNodes=" + nodeSum / n);
        }
    }
}
