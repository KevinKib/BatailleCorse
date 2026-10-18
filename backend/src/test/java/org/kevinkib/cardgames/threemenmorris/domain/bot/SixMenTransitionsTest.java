package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.threemenmorris.domain.Geometry;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class SixMenTransitionsTest {

    private static final MorrisRules FLYING = new MorrisRules(Geometry.SIX_MEN, 6, MorrisRules.LineRule.MILL,
            MorrisRules.RemovalRule.PROTECT_MILLS, MorrisRules.FlyingRule.AT_THREE, 50);

    private static MorrisTransitions.Outcome.Next next(MorrisPosition position, MorrisDecision decision) {
        MorrisTransitions.Outcome outcome = MorrisTransitions.apply(position, decision);
        assertThat(outcome, instanceOf(MorrisTransitions.Outcome.Next.class));
        return (MorrisTransitions.Outcome.Next) outcome;
    }

    @Test
    void theOpeningOffersAPlacementOnEachOfTheSixteenPoints() {
        MorrisPosition opening = SixPositions.of("................", 6, 6, 0);

        assertThat(MorrisTransitions.legal(opening), hasSize(16));
        assertThat(MorrisTransitions.legal(opening).get(0), is(new MorrisDecision.Place(0)));
    }

    @Test
    void whenTheHandsAreEmptyAPieceStepsAlongTheLinesToFreeNeighbours() {
        // White on the corner 0 (neighbours 1 and 7); black on 1 blocks one of them.
        MorrisPosition position = SixPositions.of("WB..............", 0, 0, 0);

        assertThat(MorrisTransitions.legal(position), contains(new MorrisDecision.Step(0, 7)));
    }

    @Test
    void aPlacementThatClosesALineKeepsTheSeatToMoveAndAsksForARemoval() {
        MorrisPosition position = SixPositions.of("WW......B.B.....", 4, 4, 0);

        MorrisPosition after = next(position, new MorrisDecision.Place(2)).position();

        assertThat(after.removing(), is(true));
        assertThat(after.toMove(), is(0));
        assertThat(after.inHand(), is(List.of(3, 4)));
        assertThat(after.board().get(2), is(0));
    }

    @Test
    void aStepThatClosesALineAlsoAsksForARemoval() {
        // White 0 and 1, and a white piece on 3 that steps to 2.
        MorrisPosition position = SixPositions.of("WW.W....B.B.....", 0, 0, 0);

        MorrisPosition after = next(position, new MorrisDecision.Step(3, 2)).position();

        assertThat(after.removing(), is(true));
        assertThat(after.toMove(), is(0));
    }

    @Test
    void anEstablishedMillIsNotACloseAgainWhenAnotherPieceMoves() {
        // White holds 0-1-2 already; the piece on 4 steps to 5: no new line through the destination.
        MorrisPosition position = SixPositions.of("WWW.W...B.B.B...", 0, 0, 0);

        MorrisPosition after = next(position, new MorrisDecision.Step(4, 5)).position();

        assertThat(after.removing(), is(false));
        assertThat(after.toMove(), is(1));
    }

    @Test
    void movingOutOfAMillAndBackFormsTheMillAgain() {
        // White 0, 1, 2 hold a mill; 2 steps to 3 ...
        MorrisPosition open = next(SixPositions.of("WWW.....B.B.B...", 0, 0, 0),
                new MorrisDecision.Step(2, 3)).position();
        // ... black passes the turn by a step, white steps back and closes it again.
        MorrisPosition black = next(open, new MorrisDecision.Step(8, 15)).position();
        MorrisPosition after = next(black, new MorrisDecision.Step(3, 2)).position();

        assertThat(after.removing(), is(true));
    }

    @Test
    void aPieceOutsideEveryMillIsTheOnlyLegalRemoval() {
        // Black holds the mill 8-9-10 and a lone piece on 12.
        MorrisPosition position = SixPositions.removing("WWW.....BBB.B...", 0, 0, 0);

        assertThat(MorrisTransitions.legal(position), contains(new MorrisDecision.Remove(12)));
    }

    @Test
    void whenEveryOpponentPieceIsInAMillAllOfThemMayBeTaken() {
        MorrisPosition position = SixPositions.removing("WWW.....BBB.....", 0, 0, 0);

        assertThat(MorrisTransitions.legal(position), containsInAnyOrder(
                new MorrisDecision.Remove(8), new MorrisDecision.Remove(9), new MorrisDecision.Remove(10)));
    }

    @Test
    void aRemovalClearsThePointAndPassesTheTurn() {
        MorrisPosition position = SixPositions.removing("WWW.....B.B.B.B.", 0, 0, 0);

        MorrisPosition after = next(position, new MorrisDecision.Remove(12)).position();

        assertThat(after.board().get(12), is((Integer) null));
        assertThat(after.removing(), is(false));
        assertThat(after.toMove(), is(1));
    }

    @Test
    void takingTheThirdLastOpponentPieceWins() {
        // Black has three pieces on the board and none in hand: one removal leaves two.
        MorrisPosition three = SixPositions.removing("WWW.....B.B.B...", 0, 0, 0);
        assertThat(MorrisTransitions.apply(three, new MorrisDecision.Remove(8)),
                is(new MorrisTransitions.Outcome.Win(0)));
        // With four pieces the game goes on.
        MorrisPosition four = SixPositions.removing("WWW.....B.B.B.B.", 0, 0, 0);
        assertThat(MorrisTransitions.apply(four, new MorrisDecision.Remove(8)),
                instanceOf(MorrisTransitions.Outcome.Next.class));
    }

    @Test
    void piecesInHandCountTowardsTheTwoPiecesLimit() {
        // Two black pieces on the board plus one in hand: three in all, so a removal leaves two and wins.
        MorrisPosition position = SixPositions.removing("WWW.....B.B.....", 3, 1, 0);

        assertThat(MorrisTransitions.apply(position, new MorrisDecision.Remove(10)),
                is(new MorrisTransitions.Outcome.Win(0)));
        // With two in hand the count is four: the game goes on.
        MorrisPosition more = SixPositions.removing("WWW.....B.B.....", 3, 2, 0);
        assertThat(MorrisTransitions.apply(more, new MorrisDecision.Remove(10)),
                instanceOf(MorrisTransitions.Outcome.Next.class));
    }

    @Test
    void aRemovalThatLeavesTheOpponentWithoutAMoveWins() {
        // Black 8, 9, 15 and 12 (the one taken); white walls 10, 1, 14 and 7 in and holds 4-5-6.
        MorrisPosition position = SixPositions.removing(".W..WWWWBBW.B.WB", 0, 0, 0);

        assertThat(position.board().size(), is(16));
        assertThat(MorrisTransitions.apply(position, new MorrisDecision.Remove(12)),
                is(new MorrisTransitions.Outcome.Win(0)));
    }

    @Test
    void noFlyingUnderSixMenRules() {
        // Black has three pieces: 8, 12 and 4; none flies, each steps to a free neighbour only.
        MorrisPosition position = SixPositions.of("W.W.B...B...B...", 0, 0, 1);

        List<MorrisDecision> legal = MorrisTransitions.legal(position);

        assertThat(legal, containsInAnyOrder(
                new MorrisDecision.Step(4, 3), new MorrisDecision.Step(4, 5),
                new MorrisDecision.Step(8, 9), new MorrisDecision.Step(8, 15),
                new MorrisDecision.Step(12, 11), new MorrisDecision.Step(12, 13)));
    }

    @Test
    void flyingRulesLetTheThreePieceSeatMoveToAnyFreePoint() {
        MorrisPosition built = SixPositions.of("W.W.B...B...B...", 0, 0, 1);
        MorrisPosition position = new MorrisPosition(FLYING, built.board(), built.inHand(), 1);

        List<MorrisDecision> legal = MorrisTransitions.legal(position);

        // 3 pieces times 11 free points (16 - 2 white - 3 black).
        assertThat(legal, hasSize(33));
        assertThat(legal.contains(new MorrisDecision.Step(4, 14)), is(true));
    }

    @Test
    void flyingRulesDoNotHelpASeatWithMoreThanThreePieces() {
        MorrisPosition built = SixPositions.of("W.W.B...B...B.B.", 0, 0, 1);
        MorrisPosition position = new MorrisPosition(FLYING, built.board(), built.inHand(), 1);

        assertThat(MorrisTransitions.legal(position).contains(new MorrisDecision.Step(4, 14)), is(false));
    }

    @Test
    void aPlacementDoesNotFlyEvenWhenTheSeatHasThreePieces() {
        MorrisPosition built = SixPositions.of("W.W.B...B...B...", 1, 1, 1);
        MorrisPosition position = new MorrisPosition(FLYING, built.board(), built.inHand(), 1);

        assertThat(MorrisTransitions.legal(position).stream()
                .allMatch(decision -> decision instanceof MorrisDecision.Place), is(true));
    }

    @Test
    void aStepThatBlocksTheOpponentWins() {
        // Black 8, 9, 15 is walled in by white 1, 10, 14; the white piece on 6 steps to 7 and shuts the last exit.
        MorrisPosition position = SixPositions.of(".W....W.BBW...WB", 0, 0, 0);

        assertThat(MorrisTransitions.apply(position, new MorrisDecision.Step(6, 7)),
                is(new MorrisTransitions.Outcome.Win(0)));
    }
}
