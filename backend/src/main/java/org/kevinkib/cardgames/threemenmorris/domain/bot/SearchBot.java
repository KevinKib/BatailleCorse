package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.Geometry;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The Expert level on rules the solver cannot handle (Six Men's Morris): a depth-limited negamax with
 * alpha-beta pruning. It is modest on purpose: three plies see "I close a mill, you close one back" and
 * simple forks, not the long manoeuvres of the movement phase, and a node cap keeps a decision fast.
 *
 * <p>A removal belongs to the ply that closed the mill: the seat to move does not change, so the sign does
 * not flip and no depth is spent. Moves are tried wins first, then mills, then the rest in point order; the
 * best decisions (equal scores) are returned together and one is chosen through the generator.
 *
 * <p>Evaluation for the seat to move: 100 x pieces left difference (board plus hand) + 20 x open threats
 * difference (two own pieces and a free third on a line) + 10 x mills held difference + 2 x mobility
 * difference. A won position is worth {@value #WIN} minus the plies.
 */
public final class SearchBot extends AbstractMorrisBot {

    static final int DEPTH = 3;
    static final int NODE_CAP = 200_000;
    private static final int WIN = 10_000;
    private static final int INFINITY = 1_000_000;

    /** The decisions with the best score and how many nodes the search visited. */
    record Result(List<MorrisDecision> best, int nodes) {
    }

    private final int nodeCap;

    public SearchBot(RandomGenerator random) {
        this(random, NODE_CAP);
    }

    SearchBot(RandomGenerator random, int nodeCap) {
        super(random);
        this.nodeCap = nodeCap;
    }

    @Override
    MorrisDecision play(MorrisPosition position, PlayerId seat) {
        return pick(search(position).best());
    }

    Result search(MorrisPosition position) {
        Search search = new Search(nodeCap);
        List<MorrisDecision> best = new ArrayList<>();
        int bestScore = -INFINITY;
        for (Move move : search.ordered(position)) {
            // Window (bestScore - 1, +inf): a decision as good as the best is scored exactly, a worse one is cut.
            int score = search.value(position, move.outcome(), DEPTH, Math.max(-INFINITY, bestScore - 1), INFINITY, 1);
            if (score > bestScore) {
                bestScore = score;
                best.clear();
            }
            if (score == bestScore) {
                best.add(move.decision());
            }
        }
        return new Result(best, search.nodes);
    }

    private record Move(MorrisDecision decision, MorrisTransitions.Outcome outcome) {
    }

    /** One search: its own node counter, so a bot instance holds no state between decisions. */
    private static final class Search {

        private final int nodeCap;
        int nodes;

        Search(int nodeCap) {
            this.nodeCap = nodeCap;
        }

        List<Move> ordered(MorrisPosition position) {
            List<Move> moves = new ArrayList<>();
            for (MorrisDecision decision : MorrisTransitions.legal(position)) {
                moves.add(new Move(decision, MorrisTransitions.apply(position, decision)));
            }
            moves.sort(Comparator.comparingInt(Search::rank));
            return moves;
        }

        private static int rank(Move move) {
            if (move.outcome() instanceof MorrisTransitions.Outcome.Win) {
                return 0;
            }
            return ((MorrisTransitions.Outcome.Next) move.outcome()).position().removing() ? 1 : 2;
        }

        /** The score, for the seat that plays the move, of the position it leads to. */
        int value(MorrisPosition parent, MorrisTransitions.Outcome outcome, int depth, int alpha, int beta, int ply) {
            if (outcome instanceof MorrisTransitions.Outcome.Win) {
                return WIN - ply;
            }
            MorrisPosition next = ((MorrisTransitions.Outcome.Next) outcome).position();
            if (next.toMove() == parent.toMove()) {
                return negamax(next, depth, alpha, beta, ply);
            }
            return -negamax(next, depth - 1, -beta, -alpha, ply + 1);
        }

        private int negamax(MorrisPosition position, int depth, int alpha, int beta, int ply) {
            if (nodes >= nodeCap) {
                return evaluate(position);
            }
            nodes++;
            if (depth <= 0) {
                return evaluate(position);
            }
            List<Move> moves = ordered(position);
            if (moves.isEmpty()) {
                return -(WIN - ply);
            }
            int best = -INFINITY;
            int window = alpha;
            for (Move move : moves) {
                int score = value(position, move.outcome(), depth, window, beta, ply + 1);
                if (score > best) {
                    best = score;
                }
                window = Math.max(window, best);
                if (window >= beta) {
                    break;
                }
            }
            return best;
        }

        /** Static score for the seat to move. */
        private static int evaluate(MorrisPosition position) {
            int me = position.toMove();
            int opponent = 1 - me;
            Geometry geometry = position.rules().geometry();
            int pieces = MorrisTransitions.piecesLeft(position, me) - MorrisTransitions.piecesLeft(position, opponent);
            int threats = 0;
            int mills = 0;
            for (List<Integer> line : geometry.lines()) {
                int mine = 0;
                int theirs = 0;
                for (int point : line) {
                    Integer owner = position.board().get(point);
                    if (owner != null) {
                        if (owner == me) {
                            mine++;
                        } else {
                            theirs++;
                        }
                    }
                }
                if (mine == line.size()) {
                    mills++;
                } else if (theirs == line.size()) {
                    mills--;
                } else if (mine == line.size() - 1 && theirs == 0) {
                    threats++;
                } else if (theirs == line.size() - 1 && mine == 0) {
                    threats--;
                }
            }
            int mobility = mobility(position, me) - mobility(position, opponent);
            return 100 * pieces + 20 * threats + 10 * mills + 2 * mobility;
        }

        private static int mobility(MorrisPosition position, int seat) {
            Geometry geometry = position.rules().geometry();
            int steps = 0;
            for (int point = 0; point < geometry.points(); point++) {
                if (Integer.valueOf(seat).equals(position.board().get(point))) {
                    for (int neighbour : geometry.neighbours(point)) {
                        if (position.board().get(neighbour) == null) {
                            steps++;
                        }
                    }
                }
            }
            return steps;
        }
    }
}
