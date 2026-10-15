package org.kevinkib.cardgames.mangola.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mangola for two players, played by the rules it is given ({@link MangolaRules}). The seat that owns
 * rows 1 and 2 (side A) is the one that starts. A move picks one of the mover's own pits holding enough
 * seeds and runs the whole sowing at once ({@link Sowing}); a pit whose sowing never ends is either not a
 * legal move or ends the game in a draw, depending on the rules. A player with no legal move loses; the
 * third occurrence of a position is a draw. Every mutating method is synchronized, validates, mutates,
 * bumps the version and returns what happened; a rejected command changes nothing.
 *
 * <p>With the free set-up a game starts in the PLACEMENT phase: each seat arranges its 32 seeds on its own 16
 * pits, simultaneously and without seeing the other, confirms, and may take the confirmation back until the
 * other seat has confirmed too. The second confirmation writes both layouts on the board and starts the PLAY
 * phase (side A to move). A game built from a board (a preset set-up, tests, bots) starts in PLAY.
 */
public class Mangola implements Game {

    static final int PLAYERS = 2;
    /** The seeds each seat arranges on its own half. */
    static final int SEEDS_PER_SIDE = 32;
    private static final int REPETITIONS_TO_DRAW = 3;

    private static final PlayerId SEAT_0 = new PlayerId(0);
    private static final PlayerId SEAT_1 = new PlayerId(1);

    private final GameId id;
    private final MangolaRules rules;
    private final PlayerId seatA;
    private MangolaBoard board;
    private PlayerId current;
    private Phase phase;
    private MangolaResult result = MangolaResult.ONGOING;
    private LastMove lastMove;
    private int version;
    private final Map<MangolaPosition.Key, Integer> occurrences = new HashMap<>();
    /** Each seat's layout in the order of its loop, null until it has arranged. */
    private final List<Integer>[] layouts = newLayouts();
    private final boolean[] ready = new boolean[PLAYERS];

    /** A game in the PLACEMENT phase, side A (the starting seat) to move once the layouts are revealed. */
    public Mangola(GameId id, MangolaRules rules, PlayerId seatA) {
        requireSeat(seatA);
        if (rules.setup() != MangolaSetup.FREE) {
            throw new IllegalArgumentException("Only the free set-up has a placement phase, not " + rules.setup());
        }
        this.id = id;
        this.rules = rules;
        this.seatA = seatA;
        this.board = MangolaBoard.empty();
        this.current = seatA;
        this.phase = Phase.PLACEMENT;
    }

    private Mangola(GameId id, MangolaRules rules, PlayerId seatA, MangolaBoard board, PlayerId current) {
        requireSeat(seatA);
        requireSeat(current);
        this.id = id;
        this.rules = rules;
        this.seatA = seatA;
        this.board = board;
        this.current = current;
        this.phase = Phase.PLAY;
        Side sideOfFirstSeat = this.seatA.equals(SEAT_0) ? Side.A : Side.B;
        layouts[SEAT_0.id()] = layoutOnBoard(board, sideOfFirstSeat);
        layouts[SEAT_1.id()] = layoutOnBoard(board, sideOfFirstSeat.other());
        ready[SEAT_0.id()] = true;
        ready[SEAT_1.id()] = true;
        countPosition();
    }

    @SuppressWarnings("unchecked")
    private static List<Integer>[] newLayouts() {
        return (List<Integer>[]) new List[PLAYERS];
    }

    private static List<Integer> layoutOnBoard(MangolaBoard board, Side side) {
        return MangolaGeometry.loop(side).stream().map(board::seeds).toList();
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

    /**
     * Replaces the seat's draft: 16 non-negative counts summing to 32, in the order of the seat's loop. No
     * cap per pit. Refused once the seat has confirmed.
     */
    public synchronized MangolaEvent arrange(PlayerId seat, int[] counts) throws MangolaException {
        requirePlacement();
        requireSeat(seat);
        if (ready[seat.id()]) {
            throw new AlreadyReadyException();
        }
        requireValidLayout(counts);
        layouts[seat.id()] = Arrays.stream(counts).boxed().toList();
        version++;
        return new MangolaEvent.LayoutChanged(seat);
    }

    /**
     * The seat is ready with its current draft. When both seats are ready the layouts are written on the
     * board, the play starts and the event is {@link MangolaEvent.LayoutsRevealed}.
     */
    public synchronized MangolaEvent confirmLayout(PlayerId seat) throws MangolaException {
        requirePlacement();
        requireSeat(seat);
        if (ready[seat.id()]) {
            throw new AlreadyReadyException();
        }
        if (layouts[seat.id()] == null) {
            throw new NoLayoutException();
        }
        ready[seat.id()] = true;
        version++;
        if (!ready[other(seat).id()]) {
            return new MangolaEvent.LayoutConfirmed(seat);
        }
        board = MangolaBoard.empty()
                .withLayout(sideOf(SEAT_0), toArray(layouts[SEAT_0.id()]))
                .withLayout(sideOf(SEAT_1), toArray(layouts[SEAT_1.id()]));
        phase = Phase.PLAY;
        current = seatA;
        countPosition();
        return new MangolaEvent.LayoutsRevealed();
    }

    /** Takes the seat's confirmation back, to arrange again; allowed until both seats are ready. */
    public synchronized MangolaEvent editLayout(PlayerId seat) throws MangolaException {
        requirePlacement();
        requireSeat(seat);
        if (!ready[seat.id()]) {
            throw new NotReadyException();
        }
        ready[seat.id()] = false;
        version++;
        return new MangolaEvent.LayoutWithdrawn(seat);
    }

    public synchronized MangolaEvent.Sown sow(PlayerId seat, int pit) throws MangolaException {
        if (isFinished()) {
            throw new FinishedGameException();
        }
        if (phase != Phase.PLAY) {
            throw new WrongPhaseException(Phase.PLAY, phase);
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

    /** The seat's layout in the order of its loop: its draft during placement, its starting layout after. */
    public synchronized Optional<List<Integer>> layoutOf(PlayerId seat) {
        requireSeat(seat);
        return Optional.ofNullable(layouts[seat.id()]);
    }

    /** Whether the seat has confirmed its layout (always true once the layouts are revealed). */
    public synchronized boolean isReady(PlayerId seat) {
        requireSeat(seat);
        return ready[seat.id()];
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
        if (phase != Phase.PLAY || !seat.equals(current)) {
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

    private void requirePlacement() throws MangolaException {
        if (isFinished()) {
            throw new FinishedGameException();
        }
        if (phase != Phase.PLACEMENT) {
            throw new WrongPhaseException(Phase.PLACEMENT, phase);
        }
    }

    private static void requireValidLayout(int[] counts) throws InvalidLayoutException {
        if (counts == null || counts.length != MangolaGeometry.LOOP_LENGTH) {
            throw new InvalidLayoutException("exactly " + MangolaGeometry.LOOP_LENGTH + " counts are needed");
        }
        int sum = 0;
        for (int count : counts) {
            if (count < 0) {
                throw new InvalidLayoutException("a pit cannot hold a negative number of seeds");
            }
            sum += count;
        }
        if (sum != SEEDS_PER_SIDE) {
            throw new InvalidLayoutException("the counts must sum to " + SEEDS_PER_SIDE + ", not " + sum);
        }
    }

    private static int[] toArray(List<Integer> counts) {
        return counts.stream().mapToInt(Integer::intValue).toArray();
    }

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
