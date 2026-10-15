package org.kevinkib.cardgames.mangola.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Under hidden placement the server cannot refuse a draft whose every move is endless (a capture would break
 * the cycle, and the opponent's half is unknown), so it flags it for the editor: the draft played alone on an
 * otherwise empty board.
 */
class MangolaDraftEndlessTest {

    private static final PlayerId SEAT_0 = new PlayerId(0);

    /** The spec's 13-seed fixture in the order of side A's loop: its only moves (c1, g2) are endless. */
    private static List<Integer> endlessFixture() {
        MangolaBoard board = Boards.of(EndlessSowingTest.ENDLESS_SIDE_A);
        return MangolaGeometry.loop(Side.A).stream().map(board::seeds).toList();
    }

    @Test
    void aDraftWhoseEveryMoveIsEndlessIsFlagged() {
        assertTrue(Mangola.onlyEndlessMoves(MangolaRules.OWNER, Side.A, endlessFixture()));
    }

    @Test
    void aDraftWithAFiniteMoveIsNotFlagged() {
        List<Integer> twoPerPit = Collections.nCopies(MangolaGeometry.LOOP_LENGTH, 2);
        assertFalse(Mangola.onlyEndlessMoves(MangolaRules.OWNER, Side.A, twoPerPit));
    }

    @Test
    void aDraftWithoutAnyMovablePitIsNotFlaggedBecauseItIsNotAnEndlessProblem() {
        List<Integer> singletons = Collections.nCopies(MangolaGeometry.LOOP_LENGTH, 1);
        assertFalse(Mangola.onlyEndlessMoves(MangolaRules.OWNER, Side.A, singletons));
    }

    @Test
    void whenEndlessSowingsAreADrawNothingIsFlagged() {
        MangolaRules draw = MangolaRules.OWNER.withEndless(MangolaRules.Endless.DRAW);
        assertFalse(Mangola.onlyEndlessMoves(draw, Side.A, endlessFixture()));
    }

    @Test
    void theGameAnswersFalseWithoutADraftAndForAnOrdinaryDraft() throws Exception {
        Mangola game = new Mangola(GameId.generate(), MangolaRules.OWNER, SEAT_0);
        assertFalse(game.draftOnlyEndless(SEAT_0), "no draft yet");
        game.arrange(SEAT_0, Collections.nCopies(MangolaGeometry.LOOP_LENGTH, 2).stream().mapToInt(i -> i).toArray());
        assertFalse(game.draftOnlyEndless(SEAT_0));
    }
}
