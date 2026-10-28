package org.kevinkib.cardgames.draughts.domain.bot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.draughts.domain.DraughtsMove;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;
import org.kevinkib.cardgames.draughts.domain.MoveGenerator;

class GreedyDraughtsBotTest {

    private static DraughtsMove played(DraughtsBot bot, DraughtsPosition position) {
        return ((DraughtsDecision.Play) bot.decide(position, position.toMove(), false)).move();
    }

    @Test
    void takesTheFreeCaptureAndLeavesThePoisonedOne() {
        // The man on 32 can take 27 (landing on 21, where 16 retakes it) or 28 (landing on 23, safe).
        DraughtsPosition position = BotFixtures.white("w32", "b27", "b28", "b16");

        for (long seed = 0; seed < 30; seed++) {
            DraughtsMove move = played(new GreedyDraughtsBot(new Random(seed)), position);

            assertThat(move.captures()).containsExactly(28);
            assertThat(move.to()).isEqualTo(23);
        }
    }

    @Test
    void avoidsAMoveThatLosesTwoForNothing() {
        // 32-28 lets 22 take 28 and 39 in one go; every other move is safe at two plies.
        DraughtsPosition position = BotFixtures.white("w32", "w31", "w39", "b22");

        for (long seed = 0; seed < 50; seed++) {
            DraughtsMove move = played(new GreedyDraughtsBot(new Random(seed)), position);

            assertThat(move.notation()).isNotEqualTo("32-28");
        }
    }

    @Test
    void prefersAPromotionAmongEqualMoves() {
        DraughtsPosition position = BotFixtures.white("w8", "w32", "b16");

        for (long seed = 0; seed < 30; seed++) {
            assertThat(played(new GreedyDraughtsBot(new Random(seed)), position).promotes()).isTrue();
        }
    }

    @Test
    void alwaysLegalOnRandomPositions() {
        GreedyDraughtsBot bot = new GreedyDraughtsBot(new Random(3));
        MoveGenerator generator = new MoveGenerator();
        for (DraughtsPosition position : BotFixtures.randomPositions(200, 7)) {
            assertThat(generator.legalMoves(position)).contains(played(bot, position));
        }
    }

    @Test
    void aConstantGeneratorMakesItDeterministic() {
        DraughtsPosition initial = DraughtsPosition.initial();

        assertThat(played(new GreedyDraughtsBot(BotFixtures.CONSTANT), initial))
                .isEqualTo(played(new GreedyDraughtsBot(BotFixtures.CONSTANT), initial));
    }
}
