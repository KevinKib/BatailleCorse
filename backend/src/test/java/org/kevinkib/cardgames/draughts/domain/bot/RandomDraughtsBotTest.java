package org.kevinkib.cardgames.draughts.domain.bot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.draughts.domain.DraughtsMove;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;
import org.kevinkib.cardgames.draughts.domain.MoveGenerator;

class RandomDraughtsBotTest {

    private final MoveGenerator generator = new MoveGenerator();

    @Test
    void alwaysPlaysALegalMoveOnAThousandRandomPositions() {
        RandomDraughtsBot bot = new RandomDraughtsBot(new Random(1));
        for (DraughtsPosition position : BotFixtures.randomPositions(1000, 42)) {
            DraughtsDecision decision = bot.decide(position, position.toMove(), false);

            assertThat(decision).isInstanceOf(DraughtsDecision.Play.class);
            DraughtsMove move = ((DraughtsDecision.Play) decision).move();
            assertThat(generator.legalMoves(position)).contains(move);
        }
    }

    @Test
    void picksAmongAllTheMovesNotAlwaysTheFirst() {
        RandomDraughtsBot bot = new RandomDraughtsBot(new Random(5));
        DraughtsPosition initial = DraughtsPosition.initial();

        long distinct = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> ((DraughtsDecision.Play) bot.decide(initial, initial.toMove(), false)).move())
                .distinct()
                .count();

        assertThat(distinct).isGreaterThan(3);
    }

    @Test
    void aConstantGeneratorMakesItDeterministic() {
        DraughtsPosition initial = DraughtsPosition.initial();
        List<DraughtsMove> moves = generator.legalMoves(initial);

        DraughtsDecision decision = new RandomDraughtsBot(BotFixtures.CONSTANT).decide(initial, initial.toMove(), false);

        assertThat(decision).isEqualTo(new DraughtsDecision.Play(moves.get(0)));
    }
}
