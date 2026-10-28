package org.kevinkib.cardgames.draughts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.draughts.domain.DraughtsResult.Reason;

class DrawTrackerTest {

    private final MoveGenerator generator = new MoveGenerator();

    private record Step(DrawTracker tracker, DraughtsPosition position) {
    }

    /**
     * Plays a random legal quiet king move whose resulting position is new and leaves the opponent without a
     * capture (so no capture is ever forced); fails if there is none.
     */
    private Step wander(DrawTracker tracker, DraughtsPosition position, Set<String> seen, Random random) {
        List<DraughtsMove> candidates = new ArrayList<>();
        for (DraughtsMove move : generator.legalMoves(position)) {
            if (!move.isCapture() && position.pieceAt(move.from()).isKing() && !seen.contains(generator.apply(position, move).key()) && isQuiet(generator.apply(position, move))) {
                candidates.add(move);
            }
        }
        if (candidates.isEmpty()) {
            throw new AssertionError("no fresh quiet king move from " + position);
        }
        DraughtsMove move = candidates.get(random.nextInt(candidates.size()));
        DraughtsPosition after = generator.apply(position, move);
        seen.add(after.key());
        return new Step(tracker.record(position, move, after), after);
    }

    private boolean isQuiet(DraughtsPosition position) {
        List<DraughtsMove> moves = generator.legalMoves(position);
        return moves.isEmpty() || !moves.get(0).isCapture();
    }

    private DrawTracker playPlies(DraughtsPosition start, int plies) {
        DrawTracker tracker = DrawTracker.start(start);
        DraughtsPosition position = start;
        Set<String> seen = new HashSet<>(List.of(start.key()));
        Random random = new Random(7);
        for (int i = 0; i < plies; i++) {
            Step step = wander(tracker, position, seen, random);
            tracker = step.tracker();
            position = step.position();
        }
        return tracker;
    }

    @Test
    void aFreshGameIsNotDrawn() {
        assertThat(DrawTracker.start(DraughtsPosition.initial()).drawReason()).isEmpty();
    }

    @Test
    void threefoldRepetitionDrawsOnTheThirdOccurrenceWithTheSameSideToMove() {
        DraughtsPosition p0 = Pos.white("W46", "w49", "B5", "b3");
        DrawTracker tracker = DrawTracker.start(p0);
        DraughtsPosition position = p0;
        // 46-41, 5-10, 41-46, 10-5 brings the position back
        int[][] cycle = {{46, 41}, {5, 10}, {41, 46}, {10, 5}};
        for (int lap = 0; lap < 2; lap++) {
            for (int[] step : cycle) {
                assertThat(tracker.drawReason()).isEmpty();
                DraughtsMove move = DraughtsMove.plain(step[0], step[1]);
                DraughtsPosition after = generator.apply(position, move);
                tracker = tracker.record(position, move, after);
                position = after;
            }
        }
        assertThat(position).isEqualTo(p0);
        assertThat(tracker.repetitions(p0)).isEqualTo(3);
        assertThat(tracker.drawReason()).contains(Reason.DRAW_REPETITION);
    }

    @Test
    void theSamePiecesWithTheOtherSideToMoveIsAnotherPosition() {
        DraughtsPosition white = Pos.white("W46", "w49", "B5", "b3");
        DraughtsPosition black = Pos.black("W46", "w49", "B5", "b3");
        DrawTracker tracker = DrawTracker.start(white);

        assertThat(tracker.repetitions(white)).isEqualTo(1);
        assertThat(tracker.repetitions(black)).isZero();
    }

    @Test
    void fiftyPliesOfKingMovesWithoutCaptureDrawTheGame() {
        DraughtsPosition start = Pos.white("W46", "W47", "w50", "B1", "b3");

        DrawTracker before = playPlies(start, 49);
        assertThat(before.kingOnlyPlies()).isEqualTo(49);
        assertThat(before.drawReason()).isEmpty();
        assertThat(playPlies(start, 50).drawReason()).contains(Reason.DRAW_KINGS_ONLY);
    }

    @Test
    void aManMoveResetsTheKingsOnlyCount() {
        DraughtsPosition p = Pos.white("W46", "w30", "B1", "b3");
        DraughtsMove kingMove = DraughtsMove.plain(46, 41);
        DraughtsPosition afterKing = generator.apply(p, kingMove);
        DrawTracker tracker = DrawTracker.start(p).record(p, kingMove, afterKing);
        assertThat(tracker.kingOnlyPlies()).isEqualTo(1);

        DraughtsMove blackMan = DraughtsMove.plain(3, 8);
        DraughtsPosition afterMan = generator.apply(afterKing, blackMan);
        tracker = tracker.record(afterKing, blackMan, afterMan);

        assertThat(tracker.kingOnlyPlies()).isZero();
    }

    @Test
    void aCaptureResetsTheKingsOnlyCount() {
        DraughtsPosition p = Pos.white("W46", "w49", "b32", "B1", "b3");
        DraughtsMove capture = generator.legalMoves(p).get(0);
        assertThat(capture.isCapture()).isTrue();

        DrawTracker tracker = DrawTracker.start(p).record(p, capture, generator.apply(p, capture));

        assertThat(tracker.kingOnlyPlies()).isZero();
    }

    @Test
    void threeKingsAgainstALoneKingDrawAfterSixteenMovesEach() {
        DraughtsPosition start = Pos.white("W46", "W47", "W48", "B1");

        assertThat(DrawTracker.start(start).endgameLimitPlies()).isEqualTo(32);
        assertThat(playPlies(start, 31).drawReason()).isEmpty();
        assertThat(playPlies(start, 32).drawReason()).contains(Reason.DRAW_ENDGAME);
    }

    @Test
    void twoKingsAgainstALoneKingDrawAfterFiveMovesEach() {
        DraughtsPosition start = Pos.white("W46", "W47", "B1");

        assertThat(DrawTracker.start(start).endgameLimitPlies()).isEqualTo(10);
        assertThat(playPlies(start, 9).drawReason()).isEmpty();
        assertThat(playPlies(start, 10).drawReason()).contains(Reason.DRAW_ENDGAME);
    }

    @Test
    void aKingAndAManAgainstALoneKingIsTheFiveMoveCase() {
        assertThat(DrawTracker.start(Pos.white("W46", "w30", "B1")).endgameLimitPlies()).isEqualTo(10);
    }

    @Test
    void oneKingAgainstOneKingIsTheFiveMoveCase() {
        assertThat(DrawTracker.start(Pos.white("W46", "B1")).endgameLimitPlies()).isEqualTo(10);
    }

    @Test
    void oneKingAndTwoMenAgainstALoneKingIsTheSixteenMoveCase() {
        assertThat(DrawTracker.start(Pos.white("W46", "w30", "w31", "B1")).endgameLimitPlies()).isEqualTo(32);
    }

    @Test
    void otherMaterialIsNotAShortEnding() {
        assertThat(DrawTracker.start(Pos.white("w46", "w47", "w48", "B1")).endgameLimitPlies()).isZero();
        assertThat(DrawTracker.start(Pos.white("W46", "W47", "W48", "W49", "B1")).endgameLimitPlies()).isZero();
        assertThat(DrawTracker.start(Pos.white("W46", "W47", "B1", "b2")).endgameLimitPlies()).isZero();
        assertThat(DrawTracker.start(Pos.white("W46", "b1")).endgameLimitPlies()).isZero();
        assertThat(DrawTracker.start(DraughtsPosition.initial()).endgameLimitPlies()).isZero();
    }

    @Test
    void theLoneSideCanBeBlack() {
        assertThat(DrawTracker.start(Pos.black("w46", "W47", "W48", "B1")).endgameLimitPlies()).isEqualTo(32);
        assertThat(DrawTracker.start(Pos.black("W46", "W47", "W48", "B1")).endgameLimitPlies()).isEqualTo(32);
    }

    @Test
    void aCaptureThatChangesTheMaterialStartsTheCountOfTheNewCase() {
        // three white kings against the black king (16 moves); black takes 23: two kings against one (5 moves)
        DraughtsPosition three = Pos.black("W23", "W47", "W49", "B28");
        DrawTracker tracker = DrawTracker.start(three);
        assertThat(tracker.endgameLimitPlies()).isEqualTo(32);

        DraughtsMove capture = generator.legalMoves(three).get(0);
        assertThat(capture.isCapture()).isTrue();
        tracker = tracker.record(three, capture, generator.apply(three, capture));

        assertThat(tracker.endgameLimitPlies()).isEqualTo(10);
        assertThat(tracker.endgamePlies()).isZero();
    }

    @Test
    void aGameWithMoreMaterialHasNoEndgameCount() {
        DrawTracker tracker = DrawTracker.start(DraughtsPosition.initial());

        assertThat(tracker.endgameLimitPlies()).isZero();
        assertThat(tracker.endgamePlies()).isZero();
        assertThat(tracker.drawReason()).isEmpty();
    }

    @Test
    void lossReasonsAreNoPiecesAndBlocked() {
        assertThat(generator.lossReason(Pos.white("b1"))).contains(Reason.NO_PIECES);
        assertThat(generator.lossReason(Pos.white("w31", "b26", "b27", "b22"))).contains(Reason.BLOCKED);
        assertThat(generator.lossReason(DraughtsPosition.initial())).isEmpty();
    }
}
