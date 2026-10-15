package org.kevinkib.cardgames.mangola.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The free placement phase: simultaneous drafts, confirm and withdraw, then the reveal that starts the play. */
class MangolaPlacementTest {

    private static final PlayerId SEAT_0 = new PlayerId(0);
    private static final PlayerId SEAT_1 = new PlayerId(1);

    /** 2 seeds in each of the 16 pits. */
    private static int[] twoPerPit() {
        int[] counts = new int[16];
        Arrays.fill(counts, 2);
        return counts;
    }

    /** All 32 seeds in the first pit of the loop. */
    private static int[] allInFirst() {
        int[] counts = new int[16];
        counts[0] = 32;
        return counts;
    }

    private static Mangola placement() {
        return new Mangola(GameId.generate(), MangolaRules.OWNER, SEAT_0);
    }

    private static Mangola readyToReveal() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, twoPerPit());
        game.arrange(SEAT_1, allInFirst());
        game.confirmLayout(SEAT_0);
        return game;
    }

    // ------------------------------------------------------------ construction

    @Test
    void aFreeGameStartsInPlacementWithAnEmptyBoardAndNobodyReady() {
        Mangola game = placement();
        assertEquals(Phase.PLACEMENT, game.phase());
        assertEquals(0, game.board().total());
        assertEquals(0, game.version());
        assertFalse(game.isReady(SEAT_0));
        assertFalse(game.isReady(SEAT_1));
        assertEquals(Optional.empty(), game.layoutOf(SEAT_0));
        assertEquals(Optional.empty(), game.layoutOf(SEAT_1));
        assertFalse(game.isFinished());
        assertEquals(MangolaResult.ONGOING, game.result());
        assertEquals(List.of(), game.legalPits(SEAT_0));
    }

    @Test
    void theStartingSeatIsSideAEvenInPlacement() {
        Mangola game = new Mangola(GameId.generate(), MangolaRules.OWNER, SEAT_1);
        assertEquals(SEAT_1, game.seatA());
        assertEquals(Side.A, game.sideOf(SEAT_1));
        assertEquals(SEAT_1, game.currentSeat());
    }

    @Test
    void thePublicConstructorOnlyBuildsFreeGames() {
        MangolaRules preset = MangolaRules.OWNER.withSetup(MangolaSetup.CHECKERBOARD);
        assertThrows(IllegalArgumentException.class, () -> new Mangola(GameId.generate(), preset, SEAT_0));
        assertThrows(IllegalArgumentException.class,
                () -> new Mangola(GameId.generate(), MangolaRules.OWNER, new PlayerId(2)));
    }

    // ----------------------------------------------------------------- arrange

    @Test
    void arrangeStoresTheLayoutBumpsTheVersionAndReportsTheChange() throws Exception {
        Mangola game = placement();
        int[] counts = twoPerPit();
        MangolaEvent event = game.arrange(SEAT_0, counts);
        assertEquals(new MangolaEvent.LayoutChanged(SEAT_0), event);
        assertEquals(1, game.version());
        assertEquals(Optional.of(Collections.nCopies(16, 2)), game.layoutOf(SEAT_0));
        assertEquals(Optional.empty(), game.layoutOf(SEAT_1));
        assertFalse(game.isReady(SEAT_0));
        assertEquals(Phase.PLACEMENT, game.phase());
    }

    @Test
    void theLatestLayoutReplacesThePreviousOne() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, twoPerPit());
        game.arrange(SEAT_0, allInFirst());
        assertEquals(32, game.layoutOf(SEAT_0).orElseThrow().get(0));
        assertEquals(2, game.version());
    }

    @Test
    void theLayoutIsCopiedSoLaterChangesToTheArrayDoNotLeakIn() throws Exception {
        Mangola game = placement();
        int[] counts = twoPerPit();
        game.arrange(SEAT_0, counts);
        counts[0] = 99;
        assertEquals(2, game.layoutOf(SEAT_0).orElseThrow().get(0));
    }

    @Test
    void noCapPerPitIsEnforced() throws Exception {
        placement().arrange(SEAT_0, allInFirst());
    }

    @Test
    void aLayoutMustBeSixteenNonNegativeCountsSummingToThirtyTwo() {
        Mangola game = placement();
        int[] tooFew = new int[15];
        tooFew[0] = 32;
        int[] tooMany = new int[17];
        tooMany[0] = 32;
        int[] negative = twoPerPit();
        negative[0] = -1;
        negative[1] = 5;
        int[] sum31 = twoPerPit();
        sum31[0] = 1;
        int[] sum33 = twoPerPit();
        sum33[0] = 3;
        for (int[] bad : new int[][] {null, new int[0], tooFew, tooMany, negative, sum31, sum33}) {
            assertThrows(InvalidLayoutException.class, () -> game.arrange(SEAT_0, bad));
        }
        assertEquals(0, game.version());
        assertEquals(Optional.empty(), game.layoutOf(SEAT_0));
    }

    @Test
    void arrangeRefusesAnUnknownSeat() {
        assertThrows(IllegalArgumentException.class, () -> placement().arrange(new PlayerId(2), twoPerPit()));
    }

    @Test
    void arrangeIsRefusedOnceTheSeatHasConfirmed() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, twoPerPit());
        game.confirmLayout(SEAT_0);
        assertThrows(AlreadyReadyException.class, () -> game.arrange(SEAT_0, allInFirst()));
        assertEquals(Collections.nCopies(16, 2), game.layoutOf(SEAT_0).orElseThrow());
    }

    @Test
    void arrangeIsStillAllowedToTheSeatThatHasNotConfirmed() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, twoPerPit());
        game.confirmLayout(SEAT_0);
        game.arrange(SEAT_1, allInFirst());
        assertEquals(32, game.layoutOf(SEAT_1).orElseThrow().get(0));
    }

    // ----------------------------------------------------------------- confirm

    @Test
    void confirmWithoutALayoutIsRefused() {
        Mangola game = placement();
        assertThrows(NoLayoutException.class, () -> game.confirmLayout(SEAT_0));
        assertFalse(game.isReady(SEAT_0));
        assertEquals(0, game.version());
    }

    @Test
    void confirmMarksTheSeatReadyAndBumpsTheVersion() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, twoPerPit());
        MangolaEvent event = game.confirmLayout(SEAT_0);
        assertEquals(new MangolaEvent.LayoutConfirmed(SEAT_0), event);
        assertTrue(game.isReady(SEAT_0));
        assertFalse(game.isReady(SEAT_1));
        assertEquals(2, game.version());
        assertEquals(Phase.PLACEMENT, game.phase());
        assertEquals(0, game.board().total());
    }

    @Test
    void confirmingTwiceIsRefused() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, twoPerPit());
        game.confirmLayout(SEAT_0);
        assertThrows(AlreadyReadyException.class, () -> game.confirmLayout(SEAT_0));
        assertEquals(2, game.version());
    }

    // ---------------------------------------------------------------- withdraw

    @Test
    void editLayoutTakesTheConfirmationBackAndKeepsTheDraft() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, twoPerPit());
        game.confirmLayout(SEAT_0);
        MangolaEvent event = game.editLayout(SEAT_0);
        assertEquals(new MangolaEvent.LayoutWithdrawn(SEAT_0), event);
        assertFalse(game.isReady(SEAT_0));
        assertEquals(Collections.nCopies(16, 2), game.layoutOf(SEAT_0).orElseThrow());
        assertEquals(3, game.version());
        game.arrange(SEAT_0, allInFirst());
        game.confirmLayout(SEAT_0);
        assertTrue(game.isReady(SEAT_0));
    }

    @Test
    void editLayoutWithoutAConfirmationIsRefused() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, twoPerPit());
        assertThrows(NotReadyException.class, () -> game.editLayout(SEAT_0));
        assertEquals(1, game.version());
    }

    @Test
    void editLayoutIsRefusedOnceBothAreReadyBecauseTheLayoutsAreRevealed() throws Exception {
        Mangola game = readyToReveal();
        game.confirmLayout(SEAT_1);
        assertEquals(Phase.PLAY, game.phase());
        assertThrows(WrongPhaseException.class, () -> game.editLayout(SEAT_0));
        assertThrows(WrongPhaseException.class, () -> game.editLayout(SEAT_1));
    }

    // ------------------------------------------------------------------ reveal

    @Test
    void theSecondConfirmationRevealsBothLayoutsAndStartsThePlay() throws Exception {
        Mangola game = readyToReveal();
        int versionBefore = game.version();
        MangolaEvent event = game.confirmLayout(SEAT_1);
        assertEquals(new MangolaEvent.LayoutsRevealed(), event);
        assertEquals(versionBefore + 1, game.version());
        assertEquals(Phase.PLAY, game.phase());
        assertEquals(SEAT_0, game.currentSeat());
        assertEquals(32, game.board().sideTotal(Side.A));
        assertEquals(32, game.board().sideTotal(Side.B));
        assertTrue(game.isReady(SEAT_0));
        assertTrue(game.isReady(SEAT_1));
    }

    @Test
    void layoutsAreWrittenInTheOrderOfEachSeatsLoop() throws Exception {
        Mangola game = readyToReveal();
        game.confirmLayout(SEAT_1);
        // seat 0 is side A: two per pit; seat 1 is side B: everything on the first pit of its loop, a4
        for (int pit : MangolaGeometry.loop(Side.A)) {
            assertEquals(2, game.board().seeds(pit));
        }
        assertEquals(32, game.board().seeds(MangolaGeometry.pit("a4")));
        assertEquals(64, game.board().total());
    }

    @Test
    void whenSeatOneIsSideATheLayoutsFollowTheSeatNotTheSeatNumber() throws Exception {
        Mangola game = new Mangola(GameId.generate(), MangolaRules.OWNER, SEAT_1);
        game.arrange(SEAT_0, allInFirst()); // side B
        game.arrange(SEAT_1, twoPerPit());  // side A
        game.confirmLayout(SEAT_1);
        game.confirmLayout(SEAT_0);
        assertEquals(32, game.board().seeds(MangolaGeometry.pit("a4")));
        assertEquals(2, game.board().seeds(MangolaGeometry.pit("a2")));
        assertEquals(SEAT_1, game.currentSeat());
    }

    @Test
    void theRevealGivesTheTurnToSideAWhoCanThenSow() throws Exception {
        Mangola game = readyToReveal();
        game.confirmLayout(SEAT_1);
        assertFalse(game.legalPits(SEAT_0).isEmpty());
        assertEquals(List.of(), game.legalPits(SEAT_1));
        assertInstanceOf(MangolaEvent.Sown.class, game.sow(SEAT_0, game.legalPits(SEAT_0).get(0)));
        assertEquals(64, game.board().total());
    }

    @Test
    void theRevealedPositionCountsAsTheFirstOccurrence() throws Exception {
        Mangola game = readyToReveal();
        game.confirmLayout(SEAT_1);
        assertEquals(1, game.repetitions());
    }

    @Test
    void arrangeAndConfirmAreRefusedAfterTheReveal() throws Exception {
        Mangola game = readyToReveal();
        game.confirmLayout(SEAT_1);
        int version = game.version();
        assertThrows(WrongPhaseException.class, () -> game.arrange(SEAT_0, twoPerPit()));
        assertThrows(WrongPhaseException.class, () -> game.confirmLayout(SEAT_0));
        assertEquals(version, game.version());
    }

    // ------------------------------------------------------------ other phases

    @Test
    void sowIsRefusedDuringPlacement() {
        Mangola game = placement();
        assertThrows(WrongPhaseException.class, () -> game.sow(SEAT_0, MangolaGeometry.pit("a2")));
        assertEquals(0, game.version());
    }

    @Test
    void forfeitDuringPlacementFinishesTheGameForTheOtherSeat() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, twoPerPit());
        game.forfeit(SEAT_1);
        assertTrue(game.isFinished());
        assertEquals(Phase.FINISHED, game.phase());
        assertEquals(new MangolaResult(SEAT_0, MangolaResult.Reason.FORFEIT), game.result());
        assertEquals(2, game.version());
    }

    @Test
    void placementCommandsAreRefusedOnceTheGameIsFinished() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, twoPerPit());
        game.forfeit(SEAT_1);
        assertThrows(FinishedGameException.class, () -> game.arrange(SEAT_0, twoPerPit()));
        assertThrows(FinishedGameException.class, () -> game.confirmLayout(SEAT_0));
        assertThrows(FinishedGameException.class, () -> game.editLayout(SEAT_0));
    }

    @Test
    void aGameBuiltFromALayoutIsReadyAndInPlay() {
        Mangola game = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0, Boards.of("a2:3", "c3:2"));
        assertEquals(Phase.PLAY, game.phase());
        assertTrue(game.isReady(SEAT_0));
        assertTrue(game.isReady(SEAT_1));
    }
}
