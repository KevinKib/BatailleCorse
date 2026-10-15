package org.kevinkib.cardgames.mangola.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mangola for two players, played by the rules it is given ({@link MangolaRules}). The seat that owns
 * rows 1 and 2 (side A) is the one that starts. A move picks one of the mover's own pits holding enough
 * seeds and runs the whole sowing at once ({@link Sowing}); a pit whose sowing never ends is either not a
 * legal move or ends the game in a draw, depending on the rules. A player with no legal move loses; the
 * third occurrence of a position is a draw. Every mutating method is synchronized, validates, mutates,
 * bumps the version and returns what happened; a rejected command changes nothing.
 *
 * <p>This version only knows the PLAY phase: a game is built from a given board.
 */
public class Mangola implements Game {

    private static final int REPETITIONS_TO_DRAW = 3;

    private static final PlayerId SEAT_0 = new PlayerId(0);
    private static final PlayerId SEAT_1 = new PlayerId(1);

    private final GameId id;
    private final MangolaRules rules;
    private final PlayerId seatA;
    private MangolaBoard board;
    private PlayerId current;
    private Phase phase = Phase.PLAY;
    private MangolaResult result = MangolaResult.ONGOING;
    private LastMove lastMove;
    private int version;
    private final Map<MangolaPosition.Key, Integer> occurrences = new HashMap<>();

    private Mangola(GameId id, MangolaRules rules, PlayerId seatA, MangolaBoard board, PlayerId current) {
        requireSeat(seatA);
        requireSeat(current);
        this.id = id;
        this.rules = rules;
        this.seatA = seatA;
        this.board = board;
        this.current = current;
        countPosition();
    }

    /** A game in the PLAY phase on a given board, side A (the starting seat) to move. */
    public static Mangola fromLayout(GameId id, MangolaRules rules, PlayerId seatA, MangolaBoard board) {
        return fromLayout(id, rules, seatA, board, seatA);
    }

    /** A game in the PLAY phase on a given board and seat to move (positions injected by tests and bots). */
    public static Mangola fromLayout(GameId id, MangolaRules rules, PlayerId seatA, MangolaBoard board,
                                     PlayerId toMove) {
        return new Mangola(id, rules, seatA, board, toMove);
    }

    // -------------------------------------------------------------- commands

    public synchronized MangolaEvent.Sown sow(PlayerId seat, int pit) throws MangolaException {
        if (isFinished()) {
            throw new FinishedGameException();
        }
        requireSeat(seat);
        if (!seat.equals(current)) {
            throw new NotPlayersTurnException();
        }
        if (!MangolaGeometry.isPit(pit)) {
            throw new InvalidPitException(pit);
        }
        Side side = sideOf(seat);
        if (MangolaGeometry.owner(pit) != side) {
            throw new NotYourPitException(pit);
        }
        if (board.seeds(pit) < rules.minSeedsToMove()) {
            throw new TooFewSeedsException(pit, rules.minSeedsToMove());
        }
        SowingResult sowing = Sowing.sow(board, side, pit, rules);
        if (sowing.endless() && rules.endless() == MangolaRules.Endless.ILLEGAL) {
            throw new EndlessSowingException(pit);
        }

        version++;
        MangolaEvent.Sown event = new MangolaEvent.Sown(seat, pit, sowing.trace(), sowing.captured());
        lastMove = new LastMove(seat, pit, sowing.trace());
        if (sowing.endless()) {
            finish(null, MangolaResult.Reason.ENDLESS_SOWING);
            return event;
        }
        board = sowing.board();
        current = other(seat);
        if (legalPitsOf(current).isEmpty()) {
            finish(seat, MangolaResult.Reason.NO_MOVE);
        } else if (countPosition() >= REPETITIONS_TO_DRAW) {
            finish(null, MangolaResult.Reason.REPETITION);
        }
        return event;
    }

    @Override
    public synchronized void forfeit(PlayerId loser) {
        if (isFinished() || !SEAT_0.equals(loser) && !SEAT_1.equals(loser)) {
            return;
        }
        version++;
        finish(other(loser), MangolaResult.Reason.FORFEIT);
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

    public MangolaRules rules() {
        return rules;
    }

    /** The seat that owns rows 1 and 2 and started the game. */
    public PlayerId seatA() {
        return seatA;
    }

    public Side sideOf(PlayerId seat) {
        requireSeat(seat);
        return seatA.equals(seat) ? Side.A : Side.B;
    }

    public synchronized MangolaBoard board() {
        return board;
    }

    public synchronized MangolaResult result() {
        return result;
    }

    /** The last move played, null before the first one. */
    public synchronized LastMove lastMove() {
        return lastMove;
    }

    /** The pits the seat may sow now: none outside its turn or once the game is finished. */
    public synchronized List<Integer> legalPits(PlayerId seat) {
        if (isFinished() || !seat.equals(current)) {
            return List.of();
        }
        return legalPitsOf(seat);
    }

    /** An immutable snapshot of the game for comparisons and bots. */
    public synchronized MangolaPosition position() {
        return new MangolaPosition(rules, board.toList(), current.id());
    }

    /** How many times the current position has occurred; 0 once the game is finished. */
    public synchronized int repetitions() {
        return isFinished() ? 0 : occurrences.getOrDefault(position().key(), 0);
    }

    // --------------------------------------------------------------- helpers

    private static void requireSeat(PlayerId seat) {
        if (!SEAT_0.equals(seat) && !SEAT_1.equals(seat)) {
            throw new IllegalArgumentException("No such seat: " + seat);
        }
    }

    private static PlayerId other(PlayerId seat) {
        return SEAT_0.equals(seat) ? SEAT_1 : SEAT_0;
    }

    /** Own pits with enough seeds, leaving out endless ones when the rules make them illegal. */
    private List<Integer> legalPitsOf(PlayerId seat) {
        Side side = sideOf(seat);
        List<Integer> pits = new ArrayList<>();
        for (int pit : MangolaGeometry.loop(side)) {
            if (board.seeds(pit) < rules.minSeedsToMove()) {
                continue;
            }
            if (rules.endless() == MangolaRules.Endless.ILLEGAL && Sowing.sow(board, side, pit, rules).endless()) {
                continue;
            }
            pits.add(pit);
        }
        return pits;
    }

    private int countPosition() {
        return occurrences.merge(position().key(), 1, Integer::sum);
    }

    private void finish(PlayerId winner, MangolaResult.Reason reason) {
        phase = Phase.FINISHED;
        result = new MangolaResult(winner, reason);
    }
}
