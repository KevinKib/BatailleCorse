package org.kevinkib.cardgames.draughts.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

/**
 * International draughts (10x10) for two players. The rules live in {@link MoveGenerator} (legal moves and their
 * effect) and {@link DrawTracker} (the automatic draws of article 6); this aggregate owns the seats, the turn, the
 * draw offers, the history and the end of the game. The white seat starts (seat 0 unless the host asked otherwise).
 * Every mutating method is synchronized, validates, mutates, bumps the version and returns what happened; a
 * rejected command changes nothing.
 */
public class Draughts implements Game {

    static final int PLAYERS = 2;
    /** The men each side starts with, to count what a side has taken. */
    static final int MEN_PER_SIDE = 20;

    private static final PlayerId SEAT_0 = new PlayerId(0);
    private static final PlayerId SEAT_1 = new PlayerId(1);
    private static final MoveGenerator GENERATOR = new MoveGenerator();

    /** A move together with the seat that played it. */
    public record PlayedMove(PlayerId seat, DraughtsMove move) {
    }

    private final GameId id;
    private final PlayerId whiteSeat;
    private DraughtsPosition position;
    private DrawTracker tracker;
    private Phase phase = Phase.PLAY;
    private DraughtsResult result = DraughtsResult.ONGOING;
    private int version;
    private int legalMoveGenerations;
    private List<DraughtsMove> generated;
    private int generatedAtVersion;
    private PlayerId drawOffer;
    private final boolean[] offeredSinceOwnMove = new boolean[PLAYERS];
    private PlayedMove lastMove;
    private final List<String> history = new ArrayList<>();

    /** A game from the opening position; {@code whiteSeat} plays White and moves first. */
    public Draughts(GameId id, PlayerId whiteSeat) {
        this(id, whiteSeat, DraughtsPosition.initial());
    }

    private Draughts(GameId id, PlayerId whiteSeat, DraughtsPosition position) {
        requireSeat(whiteSeat);
        this.id = id;
        this.whiteSeat = whiteSeat;
        this.position = position;
        this.tracker = DrawTracker.start(position);
    }

    /** A game that starts from {@code position} (tests and the test-only seam); no end is looked for in it. */
    public static Draughts fromPosition(GameId id, PlayerId whiteSeat, DraughtsPosition position) {
        return new Draughts(id, whiteSeat, position);
    }

    // -------------------------------------------------------------- commands

    /**
     * Plays the legal move that starts on {@code from}, ends on {@code to} and takes exactly the squares of
     * {@code captures} (a set: the order is free). An empty set stands for the only legal move with that start and
     * end; when several share them, the command is ambiguous.
     */
    public synchronized DraughtsEvent move(PlayerId seat, int from, int to, List<Integer> captures)
            throws DraughtsException {
        requireTurn(seat);
        requireSquare(from);
        requireSquare(to);
        List<Integer> taken = captures == null ? List.of() : captures;
        for (Integer square : taken) {
            if (square == null) {
                throw new InvalidSquareException();
            }
            requireSquare(square);
        }
        DraughtsMove move = findMove(from, to, taken);

        DraughtsPosition before = position;
        DraughtsPosition after = GENERATOR.apply(before, move);
        position = after;
        tracker = tracker.record(before, move, after);
        history.add(move.notation());
        lastMove = new PlayedMove(seat, move);
        version++;
        if (drawOffer != null && !drawOffer.equals(seat)) {
            drawOffer = null;
        }
        offeredSinceOwnMove[seat.id()] = false;
        checkEnd(seat);
        return new DraughtsEvent.Moved(seat, move);
    }

    /**
     * Offers a draw. Allowed at any time of the game, whoever's turn it is, when no offer is pending
     * and the seat has not offered since its own last move. The opponent's next move declines it; the proposer's own move leaves it standing.
     */
    public synchronized DraughtsEvent offerDraw(PlayerId seat) throws DraughtsException {
        requireSeat(seat);
        requireRunning();
        if (drawOffer != null) {
            throw new DrawOfferPendingException();
        }
        if (offeredSinceOwnMove[seat.id()]) {
            throw new DrawOfferNotAllowedException(DrawOfferNotAllowedException.ALREADY_OFFERED);
        }
        drawOffer = seat;
        offeredSinceOwnMove[seat.id()] = true;
        version++;
        return new DraughtsEvent.DrawOffered(seat);
    }

    /** The opponent of the offerer accepts: the game ends with no winner. */
    public synchronized DraughtsEvent acceptDraw(PlayerId seat) throws DraughtsException {
        requireAnswerable(seat);
        version++;
        finish(null, DraughtsResult.Reason.DRAW_AGREED);
        return new DraughtsEvent.DrawAccepted(seat);
    }

    /** The opponent of the offerer declines: the offer is gone and the game goes on. */
    public synchronized DraughtsEvent declineDraw(PlayerId seat) throws DraughtsException {
        requireAnswerable(seat);
        drawOffer = null;
        version++;
        return new DraughtsEvent.DrawDeclined(seat);
    }

    @Override
    public synchronized void forfeit(PlayerId loser) {
        if (isFinished() || !SEAT_0.equals(loser) && !SEAT_1.equals(loser)) {
            return;
        }
        version++;
        finish(other(loser), DraughtsResult.Reason.FORFEIT);
    }

    /**
     * Test seam: replaces the position of a running game (history, draw counts and any offer start afresh). The
     * seat to move follows the colour to move of the new position.
     */
    public synchronized void replacePosition(DraughtsPosition newPosition) {
        position = newPosition;
        tracker = DrawTracker.start(newPosition);
        history.clear();
        lastMove = null;
        drawOffer = null;
        offeredSinceOwnMove[0] = false;
        offeredSinceOwnMove[1] = false;
        phase = Phase.PLAY;
        result = DraughtsResult.ONGOING;
        version++;
        Optional<DraughtsResult.Reason> loss = GENERATOR.lossReason(newPosition);
        if (loss.isPresent()) {
            finish(other(currentSeat()), loss.get());
        }
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

    public synchronized int version() {
        return version;
    }

    public synchronized DraughtsPosition position() {
        return position;
    }

    public synchronized DraughtsResult result() {
        return result;
    }

    public PlayerId whiteSeat() {
        return whiteSeat;
    }

    public Colour colourOf(PlayerId seat) {
        requireSeat(seat);
        return whiteSeat.equals(seat) ? Colour.WHITE : Colour.BLACK;
    }

    /** The seat whose colour is to move; stays the last mover's opponent after the game ends. */
    public synchronized PlayerId currentSeat() {
        return position.toMove() == Colour.WHITE ? whiteSeat : other(whiteSeat);
    }

    /** The moves the seat may play now: none outside its turn or once the game is over. */
    public synchronized List<DraughtsMove> legalMoves(PlayerId seat) {
        requireSeat(seat);
        if (isFinished() || !seat.equals(currentSeat())) {
            return List.of();
        }
        return generate();
    }

    /** Whether the seat, on its turn, must capture. */
    public synchronized boolean captureRequired(PlayerId seat) {
        List<DraughtsMove> moves = legalMoves(seat);
        return !moves.isEmpty() && moves.get(0).isCapture();
    }

    /** The number of pieces the compulsory capture takes (majority rule); 0 when the seat need not capture. */
    public synchronized int maxCaptures(PlayerId seat) {
        List<DraughtsMove> moves = legalMoves(seat);
        return moves.isEmpty() || !moves.get(0).isCapture() ? 0 : moves.get(0).captures().size();
    }

    /** What the seat may do now: move on its turn, and the draw actions whoever's turn it is. */
    public synchronized List<Action> availableActions(PlayerId seat) {
        requireSeat(seat);
        if (isFinished()) {
            return List.of();
        }
        List<Action> actions = new ArrayList<>();
        if (seat.equals(currentSeat())) {
            actions.add(Action.MOVE);
        }
        if (drawOffer == null) {
            if (!offeredSinceOwnMove[seat.id()]) {
                actions.add(Action.OFFER_DRAW);
            }
        } else if (!drawOffer.equals(seat)) {
            actions.add(Action.ACCEPT_DRAW);
            actions.add(Action.DECLINE_DRAW);
        }
        return actions;
    }

    public synchronized int men(PlayerId seat) {
        return count(colourOf(seat), false);
    }

    public synchronized int kings(PlayerId seat) {
        return count(colourOf(seat), true);
    }

    /** The opponent pieces the seat has taken so far: what is missing from the opponent's twenty men. */
    public synchronized int captured(PlayerId seat) {
        Colour opponent = colourOf(seat).opposite();
        return Math.max(0, MEN_PER_SIDE - count(opponent, false) - count(opponent, true));
    }

    /** The seat that offered a draw and waits for an answer, null when no offer is pending. */
    public synchronized PlayerId drawOffer() {
        return drawOffer;
    }

    /** The automatic-draw counters (an immutable snapshot). */
    public synchronized DrawTracker draws() {
        return tracker;
    }

    /** How many times the current position has occurred since the last irreversible move; 0 once finished. */
    public synchronized int repetitions() {
        return isFinished() ? 0 : tracker.repetitions(position);
    }

    public synchronized PlayedMove lastMove() {
        return lastMove;
    }

    /** The notation of every move, oldest first (a copy). */
    public synchronized List<String> history() {
        return List.copyOf(history);
    }

    // --------------------------------------------------------------- helpers

    /** How many times the generator ran (a cache check). */
    synchronized int legalMoveGenerations() {
        return legalMoveGenerations;
    }

    /** The legal moves of the current position, generated once per version (every position change bumps it). */
    private List<DraughtsMove> generate() {
        if (generatedAtVersion != version || generated == null) {
            legalMoveGenerations++;
            generated = GENERATOR.legalMoves(position);
            generatedAtVersion = version;
        }
        return generated;
    }

    private DraughtsMove findMove(int from, int to, List<Integer> captures) throws DraughtsException {
        List<DraughtsMove> sameEnds = generate().stream()
                .filter(m -> m.from() == from && m.to() == to).toList();
        if (captures.isEmpty()) {
            if (sameEnds.isEmpty()) {
                throw new IllegalMoveException();
            }
            if (sameEnds.size() > 1) {
                throw new AmbiguousMoveException();
            }
            return sameEnds.get(0);
        }
        return sameEnds.stream()
                .filter(m -> new HashSet<>(m.captures()).equals(new HashSet<>(captures))
                        && m.captures().size() == captures.size())
                .findFirst().orElseThrow(IllegalMoveException::new);
    }

    /** After a move by {@code mover}: a loss of the side now to move, else an automatic draw. */
    private void checkEnd(PlayerId mover) {
        Optional<DraughtsResult.Reason> loss = GENERATOR.lossReason(position);
        if (loss.isPresent()) {
            finish(mover, loss.get());
            return;
        }
        tracker.drawReason().ifPresent(reason -> finish(null, reason));
    }

    private void finish(PlayerId winner, DraughtsResult.Reason reason) {
        phase = Phase.FINISHED;
        drawOffer = null;
        result = new DraughtsResult(winner, reason);
    }

    private int count(Colour colour, boolean kings) {
        int count = 0;
        for (int square = 1; square <= BoardGeometry.SQUARES; square++) {
            Piece piece = position.pieceAt(square);
            if (piece != null && piece.colour() == colour && piece.isKing() == kings) {
                count++;
            }
        }
        return count;
    }

    private void requireRunning() throws DraughtsException {
        if (isFinished()) {
            throw new FinishedGameException();
        }
    }

    private void requireTurn(PlayerId seat) throws DraughtsException {
        requireSeat(seat);
        requireRunning();
        if (!seat.equals(currentSeat())) {
            throw new NotPlayersTurnException();
        }
    }

    private void requireAnswerable(PlayerId seat) throws DraughtsException {
        requireSeat(seat);
        requireRunning();
        if (drawOffer == null) {
            throw new NoDrawOfferException();
        }
        if (drawOffer.equals(seat)) {
            throw new DrawOfferNotAllowedException(DrawOfferNotAllowedException.OWN_OFFER);
        }
    }

    private static void requireSquare(int square) throws InvalidSquareException {
        if (square < 1 || square > BoardGeometry.SQUARES) {
            throw new InvalidSquareException();
        }
    }

    private static void requireSeat(PlayerId seat) {
        if (!SEAT_0.equals(seat) && !SEAT_1.equals(seat)) {
            throw new IllegalArgumentException("No such seat: " + seat);
        }
    }

    private static PlayerId other(PlayerId seat) {
        return SEAT_0.equals(seat) ? SEAT_1 : SEAT_0;
    }
}
