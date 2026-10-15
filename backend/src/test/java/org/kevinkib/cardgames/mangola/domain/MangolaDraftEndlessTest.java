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

    /**
     * A real 32-seed draft found by a random search, in the order of side A's loop: 24 seeds on a2 and a single
     * seed on every other pit from b2. The singletons cannot move, and sowing a2 never ends.
     */
    static final List<Integer> ENDLESS_DRAFT = List.of(24, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1);

    private static List<Integer> endlessFixture() {
        return ENDLESS_DRAFT;
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
    void theGameFlagsTheSeatsOwnEndlessDraftOnlyWhileArranging() throws Exception {
        Mangola game = new Mangola(GameId.generate(), MangolaRules.OWNER, SEAT_0);
        game.arrange(SEAT_0, ENDLESS_DRAFT.stream().mapToInt(Integer::intValue).toArray());

        assertTrue(game.draftOnlyEndless(SEAT_0));
        assertFalse(game.draftOnlyEndless(new PlayerId(1)), "the other seat has no draft");
        game.forfeit(new PlayerId(1));
        assertFalse(game.draftOnlyEndless(SEAT_0), "not once the game is over");
    }

    @Test
    void theGameAnswersFalseWithoutADraftAndForAnOrdinaryDraft() throws Exception {
        Mangola game = new Mangola(GameId.generate(), MangolaRules.OWNER, SEAT_0);
        assertFalse(game.draftOnlyEndless(SEAT_0), "no draft yet");
        game.arrange(SEAT_0, Collections.nCopies(MangolaGeometry.LOOP_LENGTH, 2).stream().mapToInt(i -> i).toArray());
        assertFalse(game.draftOnlyEndless(SEAT_0));
    }
}
