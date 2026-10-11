package org.kevinkib.cardgames.threemenmorris.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.ArrayList;
import java.util.List;

/**
 * Three Men's Morris for two players, on board B (3x3 plus the two diagonals through the centre).
 * Seat 0 is white and starts, seat 1 is black. Each player has three pieces: they are placed one
 * per turn (PLACEMENT), then moved one step per turn along a line to a free neighbouring point
 * (MOVEMENT). The first to align their three pieces wins, in either phase. A player with no legal
 * move loses. Every mutating method is synchronized, validates, mutates, bumps the version and
 * returns what happened; a rejected command changes nothing.
 */
public class ThreeMenMorris implements Game {

    static final int PLAYERS = 2;
    static final int PIECES = 3;

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    private final GameId id;
    private final PlayerId[] board = new PlayerId[Board.POINTS];
    private final int[] inHand = new int[PLAYERS];
    private PlayerId current = WHITE;
    private Phase phase = Phase.PLACEMENT;
    private MorrisResult result = MorrisResult.ONGOING;
    private List<Integer> winningLine = List.of();
    private int version;

    public ThreeMenMorris(GameId id) {
        this.id = id;
        inHand[0] = PIECES;
        inHand[1] = PIECES;
    }

    /**
     * Test constructor: a board written row by row (nine characters, W white, B black, anything else
     * empty), the pieces still in hand and the seat to play. No win is looked for in the layout.
     */
    static ThreeMenMorris fromLayout(GameId id, String layout, int whiteInHand, int blackInHand,
                                     PlayerId current) {
        ThreeMenMorris game = new ThreeMenMorris(id);
        for (int point = 0; point < Board.POINTS; point++) {
            char c = layout.charAt(point);
            game.board[point] = c == 'W' ? WHITE : c == 'B' ? BLACK : null;
        }
        game.inHand[0] = whiteInHand;
        game.inHand[1] = blackInHand;
        game.current = current;
        game.phase = whiteInHand == 0 && blackInHand == 0 ? Phase.MOVEMENT : Phase.PLACEMENT;
        return game;
    }

    // -------------------------------------------------------------- commands

    public synchronized MorrisEvent place(PlayerId seat, int point) throws ThreeMenMorrisException {
        requireTurn(seat);
        if (phase != Phase.PLACEMENT) {
            throw new WrongPhaseException(Phase.PLACEMENT, phase);
        }
        requirePoint(point);
        if (board[point] != null) {
            throw new PointOccupiedException(point);
        }
        board[point] = seat;
        inHand[seat.id()]--;
        version++;
        if (!finishIfLine(seat)) {
            current = other(seat);
            if (inHand[0] == 0 && inHand[1] == 0) {
                phase = Phase.MOVEMENT;
                finishIfBlocked(seat);
            }
        }
        return new MorrisEvent.Placed(seat, point);
    }

    public synchronized MorrisEvent move(PlayerId seat, int from, int to) throws ThreeMenMorrisException {
        requireTurn(seat);
        if (phase != Phase.MOVEMENT) {
            throw new WrongPhaseException(Phase.MOVEMENT, phase);
        }
        requirePoint(from);
        requirePoint(to);
        if (!seat.equals(board[from])) {
            throw new NotYourPieceException(from);
        }
        if (board[to] != null) {
            throw new PointOccupiedException(to);
        }
        if (!Board.areAdjacent(from, to)) {
            throw new NotAdjacentException(from, to);
        }
        board[from] = null;
        board[to] = seat;
        version++;
        if (!finishIfLine(seat)) {
            current = other(seat);
            finishIfBlocked(seat);
        }
        return new MorrisEvent.Moved(seat, from, to);
    }

    @Override
    public synchronized void forfeit(PlayerId loser) {
        if (isFinished() || !WHITE.equals(loser) && !BLACK.equals(loser)) {
            return;
        }
        version++;
        finish(other(loser), MorrisResult.Reason.FORFEIT, List.of());
    }

    // --------------------------------------------------------------- queries

    @Override
    public GameId getId() {
        return id;
    }

    @Override
    public synchronized boolean isFinished() {
        return phase == Phase.FINISHED;
    }

    /** Both seats, always: a forfeit finishes the game but does not remove anybody. */
    @Override
    public List<PlayerId> getPlayerIds() {
        return List.of(WHITE, BLACK);
    }

    public synchronized Phase phase() {
        return phase;
    }

    public synchronized PlayerId currentSeat() {
        return current;
    }

    public synchronized int version() {
        return version;
    }

    /** The owner of the piece on the point, null when the point is free. */
    public synchronized PlayerId owner(int point) {
        return board[point];
    }

    public synchronized int piecesToPlace(PlayerId seat) {
        return inHand[seat.id()];
    }

    public synchronized MorrisResult result() {
        return result;
    }

    /** The three aligned points of the winning player, empty when the game did not end on a line. */
    public synchronized List<Integer> winningLine() {
        return winningLine;
    }

    /** The moves the seat may play now: none outside its turn or outside the movement phase. */
    public synchronized List<Move> legalMoves(PlayerId seat) {
        if (phase != Phase.MOVEMENT || !seat.equals(current)) {
            return List.of();
        }
        return movesOf(seat);
    }

    public synchronized List<Action> availableActions(PlayerId seat) {
        if (isFinished() || !seat.equals(current)) {
            return List.of();
        }
        return List.of(phase == Phase.PLACEMENT ? Action.PLACE : Action.MOVE);
    }

    // --------------------------------------------------------------- helpers

    private void requireTurn(PlayerId seat) throws ThreeMenMorrisException {
        if (isFinished()) {
            throw new FinishedGameException();
        }
        if (!seat.equals(current)) {
            throw new NotPlayersTurnException();
        }
    }

    private static void requirePoint(int point) throws InvalidPointException {
        if (!Board.isPoint(point)) {
            throw new InvalidPointException(point);
        }
    }

    private static PlayerId other(PlayerId seat) {
        return WHITE.equals(seat) ? BLACK : WHITE;
    }

    private List<Move> movesOf(PlayerId seat) {
        List<Move> moves = new ArrayList<>();
        for (int from = 0; from < Board.POINTS; from++) {
            if (seat.equals(board[from])) {
                for (int to : Board.neighbours(from)) {
                    if (board[to] == null) {
                        moves.add(new Move(from, to));
                    }
                }
            }
        }
        return moves;
    }

    private boolean finishIfLine(PlayerId seat) {
        for (List<Integer> line : Board.LINES) {
            if (line.stream().allMatch(p -> seat.equals(board[p]))) {
                finish(seat, MorrisResult.Reason.LINE, line);
                return true;
            }
        }
        return false;
    }

    /** The seat to play, now {@code current}, has no move: the player who just played wins. */
    private void finishIfBlocked(PlayerId mover) {
        if (movesOf(current).isEmpty()) {
            finish(mover, MorrisResult.Reason.BLOCKED, List.of());
        }
    }

    private void finish(PlayerId winner, MorrisResult.Reason reason, List<Integer> line) {
        phase = Phase.FINISHED;
        result = new MorrisResult(winner, reason);
        winningLine = List.copyOf(line);
    }
}
