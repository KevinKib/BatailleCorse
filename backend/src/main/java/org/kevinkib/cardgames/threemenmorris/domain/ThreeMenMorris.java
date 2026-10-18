package org.kevinkib.cardgames.threemenmorris.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Three Men's Morris for two players, played by the rules it is given ({@link MorrisRules}: the
 * geometry, the pieces per player, the placement restriction); the variant of v1 is Tapatan, board B
 * (3x3 plus the two diagonals through the centre). The white seat starts (seat 0 by default), the
 * other seat is black. Each player has the rules' number of pieces: they are placed one per turn
 * (PLACEMENT), then moved one step per turn along a line to a free neighbouring point (MOVEMENT).
 * Under {@code WIN} rules the first to align their pieces on a line wins, in either phase. Under {@code MILL}
 * rules (Six Men's Morris) a line closed by a placement or a move is a mill: the mover keeps the turn and must
 * {@link #remove} an opponent piece (mills are protected while another piece is not), a player left with two
 * pieces loses, and 50 movement plies without a removal draw. A player with no legal move loses.
 * Every mutating method is synchronized, validates, mutates, bumps the version and returns
 * what happened; a rejected command changes nothing.
 */
public class ThreeMenMorris implements Game {

    static final int PLAYERS = 2;
    private static final int REPETITIONS_TO_DRAW = 3;
    /** A player left with fewer pieces than this (board plus hand) loses, under mill rules. */
    private static final int MINIMUM_PIECES = 3;
    /** Under {@code AT_THREE} flying, a player with this many pieces on the board (and none in hand) flies. */
    private static final int FLYING_PIECES = 3;

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
    private PlayerId drawOffer;
    private final boolean[] offeredSinceOwnMove = new boolean[PLAYERS];
    private final Map<MorrisPosition.Key, Integer> occurrences = new HashMap<>();
    /** The seat that closed a mill and must now remove an opponent piece, null when no removal is pending. */
    private PlayerId pendingRemoval;
    /** The three points of the mill just closed while a removal is pending, empty otherwise. */
    private List<Integer> lastMill = List.of();
    /** Movement plies since the movement phase began or since the last removal. */
    private int quietPlies;

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
        return fromLayout(id, MorrisRules.TAPATAN, layout, whiteInHand, blackInHand, whitePieces, blackPieces,
                current);
    }

    /**
     * Like the other overloads for any rules: the layout has one character per point of the rules' geometry
     * (16 for Six Men's Morris). No mill is looked for in the layout and no removal is pending.
     */
    static ThreeMenMorris fromLayout(GameId id, MorrisRules rules, String layout, int whiteInHand, int blackInHand,
                                     int whitePieces, int blackPieces, PlayerId current) {
        ThreeMenMorris game = new ThreeMenMorris(id, rules, SEAT_0);
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
        if (game.phase == Phase.MOVEMENT) {
            game.countPosition();
        }
        return game;
    }

    // -------------------------------------------------------------- commands

    public synchronized MorrisEvent place(PlayerId seat, int point) throws ThreeMenMorrisException {
        requireTurn(seat);
        requireNoRemovalPending();
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
        moved(seat);
        List<Integer> mill = List.of();
        if (rules.lineRule() == MorrisRules.LineRule.WIN) {
            if (!finishIfLine(seat)) {
                endTurn(seat);
            }
        } else {
            mill = openRemovalIfMill(seat, point);
            if (mill.isEmpty()) {
                endTurn(seat);
            }
        }
        return new MorrisEvent.Placed(seat, point, mill);
    }

    public synchronized MorrisEvent move(PlayerId seat, int from, int to) throws ThreeMenMorrisException {
        requireTurn(seat);
        requireNoRemovalPending();
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
        if (!geometry.areAdjacent(from, to) && !flies(seat)) {
            throw new NotAdjacentException(from, to);
        }
        board[from] = null;
        board[to] = seat;
        version++;
        moved(seat);
        quietPlies++;
        List<Integer> mill = List.of();
        if (rules.lineRule() == MorrisRules.LineRule.WIN) {
            if (!finishIfLine(seat)) {
                endTurn(seat);
            }
        } else {
            mill = openRemovalIfMill(seat, to);
            if (mill.isEmpty()) {
                endTurn(seat);
            }
        }
        return new MorrisEvent.Moved(seat, from, to, mill);
    }

    /**
     * Takes the opponent piece on {@code point} after the seat closed a mill. The piece must be one the seat may
     * take now ({@link #legalRemovals}): a piece in a mill is protected unless every opponent piece is in one.
     * The removal clears the repetition counts (a position with more pieces cannot come back) and the quiet-ply
     * count, cancels a pending draw offer, and either ends the game (the opponent is down to two pieces) or
     * passes the turn.
     */
    public synchronized MorrisEvent remove(PlayerId seat, int point) throws ThreeMenMorrisException {
        requireTurn(seat);
        if (pendingRemoval == null) {
            throw new NoRemovalPendingException();
        }
        requirePoint(point);
        PlayerId opponent = other(seat);
        if (!opponent.equals(board[point])) {
            throw new NotOpponentPieceException(point);
        }
        if (!removablePoints(seat).contains(point)) {
            throw new PieceInMillException(point);
        }
        board[point] = null;
        pendingRemoval = null;
        lastMill = List.of();
        occurrences.clear();
        quietPlies = 0;
        version++;
        moved(seat);
        if (piecesLeft(opponent) < MINIMUM_PIECES) {
            finish(seat, MorrisResult.Reason.REDUCED, List.of());
        } else {
            endTurn(seat);
        }
        return new MorrisEvent.Removed(seat, point);
    }

    /**
     * Offers a draw. Allowed in the movement phase, whoever's turn it is, when no offer is pending and
     * the seat has not offered since its own last move. The next place or move cancels it.
     */
    public synchronized MorrisEvent offerDraw(PlayerId seat) throws ThreeMenMorrisException {
        requireSeat(seat);
        if (isFinished()) {
            throw new FinishedGameException();
        }
        if (phase != Phase.MOVEMENT) {
            throw new DrawOfferNotAllowedException("A draw can only be offered in the movement phase");
        }
        if (drawOffer != null) {
            throw new DrawOfferPendingException();
        }
        if (offeredSinceOwnMove[seat.id()]) {
            throw new DrawOfferNotAllowedException("You already offered a draw since your last move");
        }
        drawOffer = seat;
        offeredSinceOwnMove[seat.id()] = true;
        version++;
        return new MorrisEvent.DrawOffered(seat);
    }

    /** The opponent of the offerer accepts: the game ends with no winner. */
    public synchronized MorrisEvent acceptDraw(PlayerId seat) throws ThreeMenMorrisException {
        requireAnswerable(seat);
        version++;
        finish(null, MorrisResult.Reason.DRAW_AGREED, List.of());
        return new MorrisEvent.DrawAccepted(seat);
    }

    /** The opponent of the offerer declines: the offer is gone and the game goes on. */
    public synchronized MorrisEvent declineDraw(PlayerId seat) throws ThreeMenMorrisException {
        requireAnswerable(seat);
        drawOffer = null;
        version++;
        return new MorrisEvent.DrawDeclined(seat);
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
        if (phase != Phase.PLACEMENT || !seat.equals(current) || pendingRemoval != null) {
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
                current.id(),
                pendingRemoval != null);
    }

    /** The moves the seat may play now: none outside its turn, outside the movement phase or during a removal. */
    public synchronized List<Move> legalMoves(PlayerId seat) {
        if (phase != Phase.MOVEMENT || !seat.equals(current) || pendingRemoval != null) {
            return List.of();
        }
        return movesOf(seat);
    }

    /** The seat that offered a draw and waits for an answer, null when no offer is pending. */
    public synchronized PlayerId drawOffer() {
        return drawOffer;
    }

    /**
     * How many times the current position (board and seat to move) has occurred in the movement
     * phase; 0 during placement and once the game is finished.
     */
    public synchronized int repetitions() {
        return phase == Phase.MOVEMENT && pendingRemoval == null ? occurrences.getOrDefault(position().key(), 0) : 0;
    }

    /** The seat that closed a mill and has yet to remove an opponent piece, null when no removal is pending. */
    public synchronized PlayerId pendingRemoval() {
        return pendingRemoval;
    }

    /** The three points of the mill that opened the pending removal, empty when none is pending. */
    public synchronized List<Integer> lastMill() {
        return lastMill;
    }

    /** Movement plies since the movement phase began or since the last removal. */
    public synchronized int quietPlies() {
        return quietPlies;
    }

    /** The opponent pieces the seat may take now: empty unless the seat has a removal pending. */
    public synchronized List<Integer> legalRemovals(PlayerId seat) {
        if (isFinished() || pendingRemoval == null || !pendingRemoval.equals(seat)) {
            return List.of();
        }
        return removablePoints(seat);
    }

    /** The pieces of the seat still in the game: on the board plus in hand. */
    public synchronized int piecesLeft(PlayerId seat) {
        return piecesToPlace(seat) + piecesOnBoard(seat);
    }

    /** The pieces the seat has lost to removals so far. */
    public synchronized int lost(PlayerId seat) {
        return rules.piecesPerPlayer() - piecesLeft(seat);
    }

    /** The draw actions the seat may take now, at any time of the game, whoever's turn it is. */
    public synchronized List<Action> drawActions(PlayerId seat) {
        requireSeat(seat);
        if (phase != Phase.MOVEMENT) {
            return List.of();
        }
        if (drawOffer == null) {
            return offeredSinceOwnMove[seat.id()] ? List.of() : List.of(Action.OFFER_DRAW);
        }
        return drawOffer.equals(seat) ? List.of() : List.of(Action.ACCEPT_DRAW, Action.DECLINE_DRAW);
    }

    public synchronized List<Action> availableActions(PlayerId seat) {
        if (isFinished() || !seat.equals(current)) {
            return List.of();
        }
        if (pendingRemoval != null) {
            return List.of(Action.REMOVE);
        }
        return List.of(phase == Phase.PLACEMENT ? Action.PLACE : Action.MOVE);
    }

    // --------------------------------------------------------------- helpers

    private void requireNoRemovalPending() throws RemovalPendingException {
        if (pendingRemoval != null) {
            throw new RemovalPendingException();
        }
    }

    private void requireTurn(PlayerId seat) throws ThreeMenMorrisException {
        if (isFinished()) {
            throw new FinishedGameException();
        }
        if (!seat.equals(current)) {
            throw new NotPlayersTurnException();
        }
    }

    private static void requireSeat(PlayerId seat) {
        if (!SEAT_0.equals(seat) && !SEAT_1.equals(seat)) {
            throw new IllegalArgumentException("No such seat: " + seat);
        }
    }

    private void requireAnswerable(PlayerId seat) throws ThreeMenMorrisException {
        requireSeat(seat);
        if (isFinished()) {
            throw new FinishedGameException();
        }
        if (drawOffer == null) {
            throw new NoDrawOfferException();
        }
        if (drawOffer.equals(seat)) {
            throw new DrawOfferNotAllowedException("You cannot answer your own draw offer");
        }
    }

    /** A place or move was accepted: it cancels a pending offer and gives the mover its right to offer back. */
    private void moved(PlayerId mover) {
        drawOffer = null;
        offeredSinceOwnMove[mover.id()] = false;
    }

    /** Counts the current position (movement phase) and returns how many times it has occurred. */
    private int countPosition() {
        return occurrences.merge(position().key(), 1, Integer::sum);
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
        boolean flying = flies(seat);
        for (int from = 0; from < geometry.points(); from++) {
            if (seat.equals(board[from])) {
                if (flying) {
                    for (int to = 0; to < geometry.points(); to++) {
                        if (board[to] == null) {
                            moves.add(new Move(from, to));
                        }
                    }
                    continue;
                }
                for (int to : geometry.neighbours(from)) {
                    if (board[to] == null) {
                        moves.add(new Move(from, to));
                    }
                }
            }
        }
        return moves;
    }

    /** Under {@code AT_THREE} rules, a seat with three pieces on the board and none in hand moves to any free point. */
    private boolean flies(PlayerId seat) {
        return rules.flying() == MorrisRules.FlyingRule.AT_THREE && inHand[seat.id()] == 0
                && piecesOnBoard(seat) == FLYING_PIECES;
    }

    private int piecesOnBoard(PlayerId seat) {
        int count = 0;
        for (PlayerId owner : board) {
            if (seat.equals(owner)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Under mill rules, looks for a line through {@code point} made of the seat's pieces. When there is one and the
     * opponent has a piece on the board, the seat must remove one: the removal is made pending and the turn stays.
     * Returns the mill (the first in geometry order if two were closed), empty when there is no removal to make.
     */
    private List<Integer> openRemovalIfMill(PlayerId seat, int point) {
        if (piecesOnBoard(other(seat)) == 0) {
            return List.of();
        }
        for (List<Integer> line : geometry.lines()) {
            if (line.contains(point) && ownsAll(seat, line)) {
                pendingRemoval = seat;
                lastMill = List.copyOf(line);
                return lastMill;
            }
        }
        return List.of();
    }

    private boolean ownsAll(PlayerId seat, List<Integer> line) {
        return line.stream().allMatch(p -> seat.equals(board[p]));
    }

    private boolean inMill(PlayerId owner, int point) {
        return geometry.lines().stream().anyMatch(line -> line.contains(point) && ownsAll(owner, line));
    }

    /**
     * The opponent pieces the seat may take: those outside every mill, or every opponent piece when all of them
     * are in a mill.
     */
    private List<Integer> removablePoints(PlayerId seat) {
        PlayerId opponent = other(seat);
        List<Integer> all = new ArrayList<>();
        List<Integer> unprotected = new ArrayList<>();
        for (int point = 0; point < board.length; point++) {
            if (opponent.equals(board[point])) {
                all.add(point);
                if (!inMill(opponent, point)) {
                    unprotected.add(point);
                }
            }
        }
        return unprotected.isEmpty() ? all : unprotected;
    }

    /**
     * The mover's ply is over: pass the turn, then the end-of-turn checks. The end of the placement phase when both
     * hands are empty (White moves first, since Black places last); in the movement phase, a blocked opponent, a
     * threefold repetition, then the quiet-ply limit.
     */
    private void endTurn(PlayerId mover) {
        current = other(mover);
        if (phase == Phase.PLACEMENT) {
            if (inHand[0] == 0 && inHand[1] == 0) {
                phase = Phase.MOVEMENT;
                if (!finishIfBlocked(mover)) {
                    countPosition();
                }
            }
            return;
        }
        if (finishIfBlocked(mover)) {
            return;
        }
        if (countPosition() >= REPETITIONS_TO_DRAW) {
            finish(null, MorrisResult.Reason.DRAW_REPETITION, List.of());
        } else if (rules.quietPlyLimit() > 0 && quietPlies >= rules.quietPlyLimit()) {
            finish(null, MorrisResult.Reason.DRAW_MOVE_LIMIT, List.of());
        }
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
    private boolean finishIfBlocked(PlayerId mover) {
        if (movesOf(current).isEmpty()) {
            finish(mover, MorrisResult.Reason.BLOCKED, List.of());
            return true;
        }
        return false;
    }

    private void finish(PlayerId winner, MorrisResult.Reason reason, List<Integer> line) {
        phase = Phase.FINISHED;
        drawOffer = null;
        pendingRemoval = null;
        lastMill = List.of();
        result = new MorrisResult(winner, reason);
        winningLine = List.copyOf(line);
    }
}
