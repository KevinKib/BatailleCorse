package org.kevinkib.cardgames.draughts.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 500 random games from the opening (fixed seed): invariants of the generator and the draw tracker. */
class MoveGeneratorPropertyTest {

    private static final int GAMES = 500;
    private static final int MAX_PLIES = 400;
    private static final long SLOWEST_CALL_NANOS = 500_000_000L;

    private final MoveGenerator generator = new MoveGenerator();

    @Test
    void randomGamesKeepTheInvariants() {
        Random random = new Random(20261028L);
        int finished = 0;
        int captureMoves = 0;
        int promotions = 0;

        for (int game = 0; game < GAMES; game++) {
            DraughtsPosition position = DraughtsPosition.initial();
            DrawTracker tracker = DrawTracker.start(position);
            for (int ply = 0; ply < MAX_PLIES; ply++) {
                long started = System.nanoTime();
                List<DraughtsMove> moves = generator.legalMoves(position);
                assertThat(System.nanoTime() - started).as("legalMoves time").isLessThan(SLOWEST_CALL_NANOS);
                if (moves.isEmpty()) {
                    assertThat(generator.lossReason(position)).isPresent();
                    finished++;
                    break;
                }
                assertThat(generator.lossReason(position)).isEmpty();
                checkMoves(position, moves);

                DraughtsMove move = moves.get(random.nextInt(moves.size()));
                captureMoves += move.isCapture() ? 1 : 0;
                promotions += move.promotes() ? 1 : 0;
                DraughtsPosition after = generator.apply(position, move);
                checkApply(position, move, after);
                tracker = tracker.record(position, move, after);
                position = after;
                if (tracker.drawReason().isPresent()) {
                    finished++;
                    break;
                }
            }
        }

        // the sample is meaningful: games end, and captures and promotions happen
        assertThat(finished).isGreaterThan(GAMES / 2);
        assertThat(captureMoves).isGreaterThan(GAMES);
        assertThat(promotions).isGreaterThan(0);
    }

    private void checkMoves(DraughtsPosition position, List<DraughtsMove> moves) {
        boolean anyCapture = moves.stream().anyMatch(DraughtsMove::isCapture);
        if (anyCapture) {
            assertThat(moves).allMatch(DraughtsMove::isCapture);
            int best = moves.get(0).captures().size();
            assertThat(moves).allMatch(m -> m.captures().size() == best);
        }
        Set<String> identities = new HashSet<>();
        for (DraughtsMove move : moves) {
            Piece mover = position.pieceAt(move.from());
            assertThat(mover).isNotNull();
            assertThat(mover.colour()).isEqualTo(position.toMove());
            assertThat(move.captures()).doesNotHaveDuplicates();
            for (int captured : move.captures()) {
                Piece piece = position.pieceAt(captured);
                assertThat(piece).isNotNull();
                assertThat(piece.colour()).isEqualTo(position.toMove().opposite());
            }
            assertThat(move.path().get(move.path().size() - 1)).isEqualTo(move.to());
            // the destination is free, or is the start square itself after a rafle
            assertThat(move.to() == move.from() || position.pieceAt(move.to()) == null).isTrue();
            assertThat(move.promotes()).isEqualTo(!mover.isKing()
                    && BoardGeometry.isPromotionSquare(mover.colour(), move.to()));
            String identity = move.from() + ">" + move.to() + ">" + move.captures().stream().sorted().toList();
            assertThat(identities.add(identity)).as("duplicate move " + identity).isTrue();
        }
    }

    private void checkApply(DraughtsPosition before, DraughtsMove move, DraughtsPosition after) {
        assertThat(after.toMove()).isEqualTo(before.toMove().opposite());
        int mine = count(after, before.toMove());
        int theirs = count(after, before.toMove().opposite());
        assertThat(mine).isEqualTo(count(before, before.toMove()));
        assertThat(theirs).isEqualTo(count(before, before.toMove().opposite()) - move.captures().size());
        assertThat(mine).isBetween(0, 20);
        assertThat(theirs).isBetween(0, 20);
        assertThat(after.pieceAt(move.to())).isNotNull();
        assertThat(after.pieceAt(move.to()).colour()).isEqualTo(before.toMove());
    }

    private int count(DraughtsPosition position, Colour colour) {
        int n = 0;
        for (int square = 1; square <= BoardGeometry.SQUARES; square++) {
            Piece piece = position.pieceAt(square);
            if (piece != null && piece.colour() == colour) {
                n++;
            }
        }
        return n;
    }
}
