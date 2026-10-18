package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

class SixMenGreedyBotTest {

    private static final PlayerId WHITE = new PlayerId(0);

    private final MorrisBot bot = new GreedyBot(Positions.FIRST);

    @Test
    void closesAMillWhenItCan() {
        // White 0 and 1, black 8 and 9 (black threatens 10 too): white closes 0-1-2 first.
        MorrisPosition position = SixPositions.of("WW......BB......", 4, 4, 0);

        assertThat(bot.decide(position, WHITE, false), is(new MorrisDecision.Place(2)));
    }

    @Test
    void closesAMillByAStep() {
        MorrisPosition position = SixPositions.of("WW.W....B.B.B...", 0, 0, 0);

        assertThat(bot.decide(position, WHITE, false), is(new MorrisDecision.Step(3, 2)));
    }

    @Test
    void blocksTheLineTheOpponentWouldCloseNext() {
        // Black 8 and 9 threaten 10; white 0 and 4 cannot close anything: it must take 10.
        MorrisPosition position = SixPositions.of("W...W...BB......", 4, 4, 0);

        assertThat(bot.decide(position, WHITE, false), is(new MorrisDecision.Place(10)));
    }

    @Test
    void removesAPieceOfTheThreat() {
        // White just closed 0-1-2. Black 8 and 9 threaten 10 and black still has pieces to place;
        // taking the lone piece on 12 would not stop it.
        MorrisPosition position = SixPositions.removing("WWW.....BB..B...", 2, 2, 0);

        assertThat(bot.decide(position, WHITE, false), anyOf(
                is(new MorrisDecision.Remove(8)), is(new MorrisDecision.Remove(9))));
    }

    @Test
    void withNoThreatItRemovesThePieceWithTheMostFreeNeighbours() {
        // Black 3 (1 free neighbour), 6 (2), 8 (2) and 13 (3), none of them in a line with another.
        MorrisPosition position = SixPositions.removing("WWWB..B.B....B..", 0, 0, 0);

        assertThat(bot.decide(position, WHITE, false), is(new MorrisDecision.Remove(13)));
    }

    @Test
    void neverTakesAPieceInAMillWhileAnotherIsAvailable() {
        MorrisPosition position = SixPositions.removing("WWW.....BBB.B...", 0, 0, 0);

        assertThat(bot.decide(position, WHITE, false), is(new MorrisDecision.Remove(12)));
    }

    @Test
    void takesTheLastPieceThatWinsAtOnce() {
        // Black has three pieces; any removal wins, and the first is as good as another.
        MorrisPosition position = SixPositions.removing("WWW.....B.B.B...", 0, 0, 0);

        assertThat(bot.decide(position, WHITE, false), is(new MorrisDecision.Remove(8)));
    }

    @Test
    void withNothingToTakeOrBlockItPlaysTheFirstLegalDecisionOfTheGenerator() {
        assertThat(bot.decide(SixPositions.of("................", 6, 6, 0), WHITE, false),
                is(new MorrisDecision.Place(0)));
    }
}
