package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;

import java.util.List;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;

class SearchBotTest {

    private static final PlayerId WHITE = new PlayerId(0);

    private final SearchBot bot = new SearchBot(Positions.FIRST);

    @Test
    void makesAForkThatTheGreedyLevelDoesNotSee() {
        // White 0 and 4 can place on 2 (threatening 1 and 3) or on 6 (threatening 5 and 7): whatever black
        // blocks, white closes the other line on its second turn. Greedy only sees one ply and plays 1.
        MorrisPosition position = SixPositions.of("W...W...B...B...", 4, 4, 0);

        assertThat(new GreedyBot(Positions.FIRST).decide(position, WHITE, false), is(new MorrisDecision.Place(1)));
        assertThat(bot.decide(position, WHITE, false), anyOf(
                is(new MorrisDecision.Place(2)), is(new MorrisDecision.Place(6))));
    }

    @Test
    void closesAMillAtOnce() {
        MorrisPosition position = SixPositions.of("WW......BB......", 4, 4, 0);

        assertThat(bot.decide(position, WHITE, false), is(new MorrisDecision.Place(2)));
    }

    @Test
    void avoidsTheMoveThatLetsTheOpponentCloseAMill() {
        // Black 8 and 9 threaten 10; every placement but 10 loses a piece.
        MorrisPosition position = SixPositions.of("W...W...BB......", 4, 4, 0);

        assertThat(bot.decide(position, WHITE, false), is(new MorrisDecision.Place(10)));
    }

    @Test
    void afterAMillItRemovesAPieceOfTheThreat() {
        MorrisPosition position = SixPositions.removing("WWW.....BB..B...", 2, 2, 0);

        assertThat(bot.decide(position, WHITE, false), anyOf(
                is(new MorrisDecision.Remove(8)), is(new MorrisDecision.Remove(9))));
    }

    @Test
    void takesTheWinningRemoval() {
        MorrisPosition position = SixPositions.removing("WWW.....B.B.B...", 0, 0, 0);

        assertThat(bot.decide(position, WHITE, false), is(new MorrisDecision.Remove(8)));
    }

    @Test
    void inTheMovementPhaseItReturnsALegalStep() {
        MorrisPosition position = SixPositions.of("WW.W..W.B.B.B...", 0, 0, 1);

        MorrisDecision decision = new SearchBot(Positions.FIRST).decide(position, new PlayerId(1), false);

        assertThat(MorrisTransitions.legal(position).contains(decision), is(true));
    }

    @Test
    void staysWithinTheNodeCap() {
        MorrisPosition opening = SixPositions.of("................", 6, 6, 0);

        SearchBot capped = new SearchBot(Positions.FIRST, 500);
        SearchBot.Result result = capped.search(opening);

        assertThat(result.nodes(), lessThanOrEqualTo(500));
        assertThat(MorrisTransitions.legal(opening).contains(result.best().get(0)), is(true));
    }

    @Test
    void theDefaultCapIs200000AndAnOpeningNeedsFarFewer() {
        SearchBot.Result result = bot.search(SixPositions.of("................", 6, 6, 0));

        assertThat(SearchBot.NODE_CAP, is(200_000));
        assertThat(result.nodes(), lessThanOrEqualTo(SearchBot.NODE_CAP));
    }

    @Test
    void isDeterministicWithAConstantGenerator() {
        MorrisPosition position = SixPositions.of("W...W...B...B...", 4, 4, 0);

        assertThat(new SearchBot(Positions.FIRST).decide(position, WHITE, false),
                is(new SearchBot(Positions.FIRST).decide(position, WHITE, false)));
    }

    @Test
    void perfectLevelPlaysTheSearchOnSixInsteadOfCallingTheSolver() {
        MorrisBot perfect = MorrisBotLevel.PERFECT.bot(Positions.FIRST);
        MorrisPosition position = SixPositions.of("W...W...B...B...", 4, 4, 0);

        MorrisDecision decision = perfect.decide(position, WHITE, false);

        assertThat(decision, is(not(new MorrisDecision.Place(1))));
        assertThat(MorrisTransitions.legal(position).contains(decision), is(true));
    }

    @Test
    void neverReturnsAnIllegalDecisionOnThousandsOfRandomSixPositions() {
        Random random = new Random(42);
        SearchBot searching = new SearchBot(random);
        GreedyBot greedy = new GreedyBot(random);
        int checked = 0;
        while (checked < 1_000) {
            MorrisPosition position = SixPositions.of("................", 6, 6, 0);
            for (int ply = 0; ply < 120 && checked < 1_000; ply++) {
                List<MorrisDecision> legal = MorrisTransitions.legal(position);
                if (legal.isEmpty()) {
                    break;
                }
                MorrisDecision decision = searching.decide(position, new PlayerId(position.toMove()), false);
                assertThat(legal.contains(decision), is(true));
                checked++;
                // Walk the game forward with a mix of random and greedy decisions to reach varied positions.
                MorrisDecision step = random.nextBoolean()
                        ? legal.get(random.nextInt(legal.size()))
                        : greedy.decide(position, new PlayerId(position.toMove()), false);
                MorrisTransitions.Outcome outcome = MorrisTransitions.apply(position, step);
                if (!(outcome instanceof MorrisTransitions.Outcome.Next next)) {
                    break;
                }
                position = next.position();
            }
        }
        assertThat(checked, is(1_000));
    }
}
