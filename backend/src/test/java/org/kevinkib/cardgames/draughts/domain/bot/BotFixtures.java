package org.kevinkib.cardgames.draughts.domain.bot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;
import org.kevinkib.cardgames.draughts.domain.BoardGeometry;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.DraughtsMove;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;
import org.kevinkib.cardgames.draughts.domain.MoveGenerator;

/** Test helpers: positions from specs such as {@code "w32"} (white man on 32), and random playouts. */
final class BotFixtures {

    /** Always the smallest value: every tie-break goes to the first candidate. */
    static final RandomGenerator CONSTANT = new RandomGenerator() {
        @Override
        public long nextLong() {
            return 0;
        }
    };

    private BotFixtures() {
    }

    static DraughtsPosition position(Colour toMove, String... specs) {
        char[] layout = new char[BoardGeometry.SQUARES];
        Arrays.fill(layout, '.');
        for (String spec : specs) {
            layout[Integer.parseInt(spec.substring(1)) - 1] = spec.charAt(0);
        }
        return DraughtsPosition.fromLayout(new String(layout), toMove);
    }

    static DraughtsPosition white(String... specs) {
        return position(Colour.WHITE, specs);
    }

    static DraughtsPosition black(String... specs) {
        return position(Colour.BLACK, specs);
    }

    /** Positions reached by random playouts from the initial position, each with at least one legal move. */
    static List<DraughtsPosition> randomPositions(int count, long seed) {
        java.util.Random random = new java.util.Random(seed);
        MoveGenerator generator = new MoveGenerator();
        List<DraughtsPosition> positions = new ArrayList<>();
        while (positions.size() < count) {
            DraughtsPosition position = DraughtsPosition.initial();
            int length = random.nextInt(90);
            for (int ply = 0; ply < length; ply++) {
                List<DraughtsMove> moves = generator.legalMoves(position);
                if (moves.isEmpty()) {
                    break;
                }
                position = generator.apply(position, moves.get(random.nextInt(moves.size())));
            }
            if (!generator.legalMoves(position).isEmpty()) {
                positions.add(position);
            }
        }
        return positions;
    }
}
