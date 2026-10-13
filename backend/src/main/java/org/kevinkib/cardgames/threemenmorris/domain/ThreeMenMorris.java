package org.kevinkib.cardgames.threemenmorris.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Three Men's Morris for two players, played by the rules it is given ({@link MorrisRules}: the
 * geometry, the pieces per player, the placement restriction); the variant of v1 is Tapatan, board B
 * (3x3 plus the two diagonals through the centre). The white seat starts (seat 0 by default), the
 * other seat is black. Each player has the rules' number of pieces: they are placed one per turn
 * (PLACEMENT), then moved one step per turn along a line to a free neighbouring point (MOVEMENT).
 * The first to align their pieces on a line wins, in either phase. A player with no legal move
 * loses. Every mutating method is synchronized, validates, mutates, bumps the version and returns
 * what happened; a rejected command changes nothing.
 */
public class ThreeMenMorris implements Game {

    static final int PLAYERS = 2;

    private static final PlayerId SEAT_0 = new PlayerId(0);
    private static final PlayerId SEAT_1 = new PlayerId(1);

    private final GameId id;
    private final MorrisRules rules;
    private final Geometry geometry;
    private final PlayerId whiteSeat;
    private final PlayerId[] board;
    private final int[] inHand = new int[PLAYERS];
    private PlayerId current;
    private Phase phase = Phase.PLACEMENT;
    private MorrisResult result = MorrisResult.ONGOING;
    private List<Integer> winningLine = List.of();
    private int version;

    /** A game of Tapatan, seat 0 white. */
    public ThreeMenMorris(GameId id) {
        this(id, MorrisRules.TAPATAN, SEAT_0);
    }

    public ThreeMenMorris(GameId id, MorrisRules rules, PlayerId whiteSeat) {
        if (!SEAT_0.equals(whiteSeat) && !SEAT_1.equals(whiteSeat)) {
            throw new IllegalArgumentException("No such seat: " + whiteSeat);
        }
        this.id = id;
        this.rules = rules;
        this.geometry = rules.geometry();
        this.whiteSeat = whiteSeat;
        this.board = new PlayerId[geometry.points()];
        this.current = whiteSeat;
        inHand[0] = rules.piecesPerPlayer();
        inHand[1] = rules.piecesPerPlayer();
    }

    /**
     * Test constructor: a Tapatan board written row by row (nine characters, W seat 0, B seat 1,
     * anything else empty), the pieces still in hand and the seat to play. No win is looked for in the
     * layout. Each player must hold three pieces, on the board and in hand together.
     */
    static ThreeMenMorris fromLayout(GameId id, String layout, int whiteInHand, int blackInHand,
                                     PlayerId current) {
        return fromLayout(id, layout, whiteInHand, blackInHand, MorrisRules.TAPATAN.piecesPerPlayer(),
                MorrisRules.TAPATAN.piecesPerPlayer(), current);
    }

    /**
     * Like the other overload, for artificial positions where the players hold another number of
     * pieces (for example to test the blocked rule, which three pieces each cannot reach without an
     * alignment first).
     */
    static ThreeMenMorris fromLayout(GameId id, String layout, int whiteInHand, int blackInHand,
                                     int whitePieces, int blackPieces, PlayerId current) {
        ThreeMenMorris game = new ThreeMenMorris(id);
        int points = game.geometry.points();
        if (layout == null || layout.length() != points) {
            throw new IllegalArgumentException("A layout has exactly " + points + " characters");
        }
        if (!SEAT_0.equals(current) && !SEAT_1.equals(current)) {
            throw new IllegalArgumentException("No such seat: " + current);
        }
        int[] onBoard = new int[PLAYERS];
        for (int point = 0; point < points; point++) {
            char c = layout.charAt(point);
            game.board[point] = c == 'W' ? SEAT_0 : c == 'B' ? SEAT_1 : null;
            if (game.board[point] != null) {
                onBoard[game.board[point].id()]++;
            }
        }
        if (onBoard[0] + whiteInHand != whitePieces || onBoard[1] + blackInHand != blackPieces) {
            throw new IllegalArgumentException("Pieces on the board plus in hand must be " + whitePieces
                    + " for white and " + blackPieces + " for black");
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
        if (!geometry.areAdjacent(from, to)) {
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
        if (isFinished() || !SEAT_0.equals(loser) && !SEAT_1.equals(loser)) {
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
        return List.of(SEAT_0, SEAT_1);
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
        if (!geometry.isPoint(point)) {
            throw new InvalidQueryException("No such point: " + point);
        }
        return board[point];
    }

    /** The owner of each point, row by row (null entries are free points): a snapshot. */
    public synchronized List<PlayerId> board() {
        return Arrays.asList(board.clone());
    }

    public synchronized int piecesToPlace(PlayerId seat) {
        if (seat == null || seat.id() == null || seat.id() < 0 || seat.id() >= PLAYERS) {
            throw new InvalidQueryException("No such seat: " + seat);
        }
        return inHand[seat.id()];
    }

    public synchronized MorrisResult result() {
        return result;
    }

    /** The three aligned points of the winning player, empty when the game did not end on a line. */
    public synchronized List<Integer> winningLine() {
        return winningLine;
    }

    public MorrisRules rules() {
        return rules;
    }

    public PlayerId whiteSeat() {
        return whiteSeat;
    }

    public Colour colourOf(PlayerId seat) {
        if (!SEAT_0.equals(seat) && !SEAT_1.equals(seat)) {
            throw new InvalidQueryException("No such seat: " + seat);
        }
        return whiteSeat.equals(seat) ? Colour.WHITE : Colour.BLACK;
    }

    /** The free points the seat may place on now: none outside its turn or outside the placement phase. */
    public synchronized List<Integer> legalPlacements(PlayerId seat) {
        if (phase != Phase.PLACEMENT || !seat.equals(current)) {
            return List.of();
        }
        List<Integer> points = new ArrayList<>();
        for (int point = 0; point < board.length; point++) {
            if (board[point] == null) {
                points.add(point);
            }
        }
        return points;
    }

    /** An immutable snapshot of the game for comparisons and bots. */
    public synchronized MorrisPosition position() {
        return new MorrisPosition(rules,
                Arrays.stream(board).map(owner -> owner == null ? null : owner.id()).toList(),
                List.of(inHand[0], inHand[1]),
                current.id());
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

    private void requirePoint(int point) throws InvalidPointException {
        if (!geometry.isPoint(point)) {
            throw new InvalidPointException(point);
        }
    }

    private static PlayerId other(PlayerId seat) {
        return SEAT_0.equals(seat) ? SEAT_1 : SEAT_0;
    }

    private List<Move> movesOf(PlayerId seat) {
        List<Move> moves = new ArrayList<>();
        for (int from = 0; from < geometry.points(); from++) {
            if (seat.equals(board[from])) {
                for (int to : geometry.neighbours(from)) {
                    if (board[to] == null) {
                        moves.add(new Move(from, to));
                    }
                }
            }
        }
        return moves;
    }

    private boolean finishIfLine(PlayerId seat) {
        for (List<Integer> line : geometry.lines()) {
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
