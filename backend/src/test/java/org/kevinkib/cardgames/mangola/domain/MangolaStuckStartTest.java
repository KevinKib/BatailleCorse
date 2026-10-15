package org.kevinkib.cardgames.mangola.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Owner decision (2026-10-15): confirming is refused when the revealed board would leave the starting seat
 * without a legal move. The check needs both halves (a capture breaks a cycle, so one half alone settles
 * nothing), so it runs at the confirmation that completes the pair, and refuses that confirmation whichever
 * seat it comes from. The non-starting seat is not constrained by it.
 */
class MangolaStuckStartTest {

    private static final PlayerId SEAT_0 = new PlayerId(0);
    private static final PlayerId SEAT_1 = new PlayerId(1);

    /** Side A: 24 seeds on a2 and singletons. Against {@link #FACING_A} every sowing of a2 never ends. */
    private static final List<Integer> STUCK_A = List.of(24, 1, 0, 1, 0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 1, 1);
    private static final List<Integer> FACING_A = List.of(2, 1, 1, 1, 1, 2, 3, 0, 3, 0, 1, 5, 2, 2, 3, 5);
    /** Side B: the same kind of draft, endless against {@link #FACING_B} (found by a random search). */
    private static final List<Integer> STUCK_B = List.of(24, 1, 0, 1, 0, 1, 0, 1, 0, 1, 1, 1, 0, 1, 0, 0);
    private static final List<Integer> FACING_B = List.of(4, 2, 5, 0, 2, 2, 1, 0, 2, 1, 5, 0, 2, 0, 4, 2);

    private static int[] array(List<Integer> counts) {
        return counts.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int[] twoPerPit() {
        int[] counts = new int[16];
        Arrays.fill(counts, 2);
        return counts;
    }

    private static Mangola placement() {
        return new Mangola(GameId.generate(), MangolaRules.OWNER, SEAT_0);
    }

    // The documented claim: one half alone neither settles nor excludes a stuck start.

    @Test
    void theFixturesAreStuckOnTheFullBoardOnly() {
        MangolaBoard board = MangolaBoard.empty()
                .withLayout(Side.A, array(STUCK_A)).withLayout(Side.B, array(FACING_A));
        Mangola game = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0, board);
        assertEquals(List.of(), game.legalPits(SEAT_0), "stuck on the full board");
        assertFalse(Mangola.onlyEndlessMoves(MangolaRules.OWNER, Side.A, STUCK_A),
                "yet not flagged alone: its a2 has a finite move on an empty opposite half");
    }

    // ---------------------------------------------------------------- refusals

    @Test
    void theStarterCompletingThePairIsRefusedWhenItsStartWouldBeStuck() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, array(STUCK_A));
        game.arrange(SEAT_1, array(FACING_A));
        game.confirmLayout(SEAT_1);
        int versionBefore = game.version();

        StuckStartException refused = assertThrows(StuckStartException.class, () -> game.confirmLayout(SEAT_0));

        assertEquals(StuckStartException.LOCALE_KEY, refused.getMessage());
        assertEquals("mangola.errors.stuckStart", refused.getMessage());
        assertEquals(Phase.PLACEMENT, game.phase());
        assertFalse(game.isReady(SEAT_0));
        assertTrue(game.isReady(SEAT_1), "the other seat stays confirmed");
        assertEquals(versionBefore, game.version(), "a refusal changes nothing");
    }

    @Test
    void theNonStarterCompletingThePairIsRefusedToo() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, array(STUCK_A));
        game.arrange(SEAT_1, array(FACING_A));
        game.confirmLayout(SEAT_0);
        int versionBefore = game.version();

        assertThrows(StuckStartException.class, () -> game.confirmLayout(SEAT_1));

        assertEquals(Phase.PLACEMENT, game.phase());
        assertFalse(game.isReady(SEAT_1));
        assertTrue(game.isReady(SEAT_0));
        assertEquals(versionBefore, game.version());
    }

    @Test
    void theRefusedSeatCanArrangeAgainAndConfirm() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, array(STUCK_A));
        game.arrange(SEAT_1, array(FACING_A));
        game.confirmLayout(SEAT_0);
        assertThrows(StuckStartException.class, () -> game.confirmLayout(SEAT_1));

        game.arrange(SEAT_1, twoPerPit());
        MangolaEvent event = game.confirmLayout(SEAT_1);

        assertInstanceOf(MangolaEvent.LayoutsRevealed.class, event);
        assertEquals(Phase.PLAY, game.phase());
        assertFalse(game.legalPits(SEAT_0).isEmpty());
    }

    @Test
    void theStarterCanTakeItsConfirmationBackAfterTheOtherSeatWasRefused() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, array(STUCK_A));
        game.arrange(SEAT_1, array(FACING_A));
        game.confirmLayout(SEAT_0);
        assertThrows(StuckStartException.class, () -> game.confirmLayout(SEAT_1));

        game.editLayout(SEAT_0);
        game.arrange(SEAT_0, twoPerPit());
        game.confirmLayout(SEAT_0);
        MangolaEvent event = game.confirmLayout(SEAT_1);

        assertInstanceOf(MangolaEvent.LayoutsRevealed.class, event);
        assertEquals(Phase.PLAY, game.phase());
    }

    // ---------------------------------------------------------- what is not refused

    @Test
    void theFirstConfirmationIsNeverCheckedEvenOnADraftThatIsEndlessAlone() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, array(MangolaDraftEndlessTest.ENDLESS_DRAFT));

        MangolaEvent event = game.confirmLayout(SEAT_0);

        assertInstanceOf(MangolaEvent.LayoutConfirmed.class, event);
    }

    @Test
    void aDraftEndlessAloneIsAcceptedWhenTheOtherHalfBreaksTheCycle() throws Exception {
        List<Integer> endlessAlone = MangolaDraftEndlessTest.ENDLESS_DRAFT;
        Mangola game = placement();
        game.arrange(SEAT_0, array(endlessAlone));
        game.arrange(SEAT_1, array(List.of(0, 4, 0, 1, 2, 2, 1, 2, 0, 6, 2, 6, 2, 1, 2, 1)));
        game.confirmLayout(SEAT_0);

        MangolaEvent event = game.confirmLayout(SEAT_1);

        assertInstanceOf(MangolaEvent.LayoutsRevealed.class, event);
        assertFalse(game.legalPits(SEAT_0).isEmpty());
    }

    @Test
    void theNonStartingSeatIsNotConstrainedWhenItIsTheOneWithoutAMove() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, array(FACING_B));
        game.arrange(SEAT_1, array(STUCK_B));
        game.confirmLayout(SEAT_0);

        MangolaEvent event = game.confirmLayout(SEAT_1);

        assertInstanceOf(MangolaEvent.LayoutsRevealed.class, event);
        assertEquals(Phase.PLAY, game.phase());
        assertFalse(game.legalPits(SEAT_0).isEmpty(), "the starter can play");
    }

    @Test
    void whenEndlessSowingsAreADrawTheStarterIsNeverStuck() throws Exception {
        Mangola game = new Mangola(GameId.generate(), MangolaRules.OWNER.withEndless(MangolaRules.Endless.DRAW),
                SEAT_0);
        game.arrange(SEAT_0, array(STUCK_A));
        game.arrange(SEAT_1, array(FACING_A));
        game.confirmLayout(SEAT_0);

        assertInstanceOf(MangolaEvent.LayoutsRevealed.class, game.confirmLayout(SEAT_1));
    }

    @Test
    void aSecondStarterSeatIsCheckedOnItsOwnHalf() throws Exception {
        Mangola game = new Mangola(GameId.generate(), MangolaRules.OWNER, SEAT_1);
        game.arrange(SEAT_1, array(STUCK_A));
        game.arrange(SEAT_0, array(FACING_A));
        game.confirmLayout(SEAT_1);

        assertThrows(StuckStartException.class, () -> game.confirmLayout(SEAT_0));
    }
}
