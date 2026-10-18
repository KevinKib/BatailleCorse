package org.kevinkib.cardgames.threemenmorris.domain.offline;

import org.kevinkib.cardgames.threemenmorris.domain.Geometry;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A deliberately naive game search for the cross-check of the compact solver: absolute colours (not seen
 * from the seat to move), one object-keyed node per position reachable from the start, successor lists
 * written out, and a round-by-round retrograde analysis on snapshots. It shares no code with
 * {@link SixMenSolver}; it is only fast enough for a reduced game (3 or 4 pieces each).
 *
 * <p>Rules: place one piece per turn until both hands are empty, then move one step; a line of three
 * through the piece just placed or moved is a mill and removes one opponent piece (not one inside a mill
 * unless all are); a player left with two pieces (board plus hand) loses; a player who cannot move loses;
 * everything else is played on, repetition and the quiet-move limit are not modelled (a cycle is a draw).
 */
final class BruteForceMorris {

    /** Colour 0 is White, 1 is Black. */
    record Node(List<Integer> board, int whiteHand, int blackHand, int toMove, boolean reduced) {
        int hand(int colour) {
            return colour == 0 ? whiteHand : blackHand;
        }
    }

    /** What the seat to move can force: {@code outcome} is +1 win, -1 loss, 0 draw; depth in plies. */
    record Verdict(int outcome, int depth) {
    }

    private final Geometry geometry;
    private final int pieces;
    private final Map<Node, Integer> ids = new HashMap<>();
    private final List<Node> nodes = new ArrayList<>();
    private final List<int[]> successors = new ArrayList<>();
    private int[] kind;
    private int[] depth;

    BruteForceMorris(MorrisRules rules) {
        this.geometry = rules.geometry();
        this.pieces = rules.piecesPerPlayer();
    }

    Node start() {
        List<Integer> board = new ArrayList<>();
        for (int i = 0; i < geometry.points(); i++) {
            board.add(-1);
        }
        return new Node(board, pieces, pieces, 0, false);
    }

    /** Explores every reachable node, then solves the whole graph. */
    void solve() {
        explore();
        analyse();
    }

    int nodeCount() {
        return nodes.size();
    }

    List<Node> nodes() {
        return nodes;
    }

    Verdict verdict(Node node) {
        int id = ids.get(node);
        return new Verdict(kind[id], depth[id]);
    }

    private int idOf(Node node) {
        Integer known = ids.get(node);
        if (known != null) {
            return known;
        }
        int id = nodes.size();
        ids.put(node, id);
        nodes.add(node);
        successors.add(null);
        return id;
    }

    private void explore() {
        idOf(start());
        for (int next = 0; next < nodes.size(); next++) {
            List<Node> children = children(nodes.get(next));
            int[] childIds = new int[children.size()];
            for (int i = 0; i < childIds.length; i++) {
                childIds[i] = idOf(children.get(i));
            }
            successors.set(next, childIds);
        }
    }

    private List<Node> children(Node node) {
        List<Node> result = new ArrayList<>();
        if (node.reduced()) {
            return result;
        }
        int mover = node.toMove();
        if (node.hand(mover) > 0) {
            for (int point = 0; point < geometry.points(); point++) {
                if (node.board().get(point) == -1) {
                    List<Integer> board = new ArrayList<>(node.board());
                    board.set(point, mover);
                    finish(node, board, point, true, result);
                }
            }
        } else {
            for (int from = 0; from < geometry.points(); from++) {
                if (node.board().get(from) != mover) {
                    continue;
                }
                for (int to : geometry.neighbours(from)) {
                    if (node.board().get(to) == -1) {
                        List<Integer> board = new ArrayList<>(node.board());
                        board.set(from, -1);
                        board.set(to, mover);
                        finish(node, board, to, false, result);
                    }
                }
            }
        }
        return result;
    }

    private void finish(Node before, List<Integer> board, int point, boolean placed, List<Node> out) {
        int mover = before.toMove();
        int opponent = 1 - mover;
        int whiteHand = before.whiteHand() - (placed && mover == 0 ? 1 : 0);
        int blackHand = before.blackHand() - (placed && mover == 1 ? 1 : 0);
        boolean mill = false;
        for (List<Integer> line : geometry.lines()) {
            if (line.contains(point) && line.stream().allMatch(p -> board.get(p) == mover)) {
                mill = true;
            }
        }
        List<Integer> opponentPieces = new ArrayList<>();
        for (int p = 0; p < board.size(); p++) {
            if (board.get(p) == opponent) {
                opponentPieces.add(p);
            }
        }
        if (!mill || opponentPieces.isEmpty()) {
            out.add(next(board, whiteHand, blackHand, opponent));
            return;
        }
        List<Integer> loose = opponentPieces.stream().filter(p -> !inMill(board, p, opponent)).toList();
        for (int taken : loose.isEmpty() ? opponentPieces : loose) {
            List<Integer> after = new ArrayList<>(board);
            after.set(taken, -1);
            out.add(next(after, whiteHand, blackHand, opponent));
        }
    }

    private boolean inMill(List<Integer> board, int point, int colour) {
        for (List<Integer> line : geometry.lines()) {
            if (line.contains(point) && line.stream().allMatch(p -> board.get(p) == colour)) {
                return true;
            }
        }
        return false;
    }

    private Node next(List<Integer> board, int whiteHand, int blackHand, int toMove) {
        int whiteTotal = whiteHand + (int) board.stream().filter(c -> c == 0).count();
        int blackTotal = blackHand + (int) board.stream().filter(c -> c == 1).count();
        boolean reduced = whiteTotal <= 2 || blackTotal <= 2;
        return new Node(List.copyOf(board), whiteHand, blackHand, toMove, reduced);
    }

    /** Round r resolves the wins and losses of depth r from the snapshot of the rounds before. */
    private void analyse() {
        int n = nodes.size();
        kind = new int[n];
        depth = new int[n];
        boolean[] resolved = new boolean[n];
        for (int round = 0; ; round++) {
            List<Integer> newWins = new ArrayList<>();
            List<Integer> newLosses = new ArrayList<>();
            for (int id = 0; id < n; id++) {
                if (resolved[id]) {
                    continue;
                }
                int[] children = successors.get(id);
                if (round == 0) {
                    if (children.length == 0) {
                        newLosses.add(id);
                    }
                    continue;
                }
                boolean winsNow = false;
                boolean allWon = true;
                int maxWon = -1;
                for (int child : children) {
                    if (resolved[child] && kind[child] == -1 && depth[child] == round - 1) {
                        winsNow = true;
                    }
                    if (resolved[child] && kind[child] == 1) {
                        maxWon = Math.max(maxWon, depth[child]);
                    } else {
                        allWon = false;
                    }
                }
                if (winsNow) {
                    newWins.add(id);
                } else if (allWon && maxWon == round - 1) {
                    newLosses.add(id);
                }
            }
            if (newWins.isEmpty() && newLosses.isEmpty()) {
                return;
            }
            for (int id : newWins) {
                resolved[id] = true;
                kind[id] = 1;
                depth[id] = round;
            }
            for (int id : newLosses) {
                resolved[id] = true;
                kind[id] = -1;
                depth[id] = round;
            }
        }
    }
}
