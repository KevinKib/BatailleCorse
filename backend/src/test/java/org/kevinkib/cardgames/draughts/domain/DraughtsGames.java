package org.kevinkib.cardgames.draughts.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

/** Test helpers shared by the draughts aggregate tests (also used from the presentation tests). */
public final class DraughtsGames {

    public static final PlayerId WHITE = new PlayerId(0);
    public static final PlayerId BLACK = new PlayerId(1);

    private static final MoveGenerator GENERATOR = new MoveGenerator();

    private DraughtsGames() {
    }

    public static Draughts opening() {
        return new Draughts(GameId.generate(), WHITE, true);
    }

    /** Seat 0 plays White, offers allowed. */
    public static Draughts from(DraughtsPosition position) {
        return Draughts.fromPosition(GameId.generate(), WHITE, position, true);
    }

    public static Draughts fromWithoutOffers(DraughtsPosition position) {
        return Draughts.fromPosition(GameId.generate(), WHITE, position, false);
    }

    /** Plays the move written {@code from-to} or {@code fromxto} for the side to move, with its captured set. */
    public static void play(Draughts game, String notation) throws Exception {
        PlayerId seat = game.currentSeat();
        DraughtsMove move = game.legalMoves(seat).stream()
                .filter(m -> m.notation().equals(notation)).findFirst()
                .orElseThrow(() -> new AssertionError(notation + " is not legal in " + game.position()));
        game.move(seat, move.from(), move.to(), move.captures());
    }

    /**
     * Plays {@code plies} quiet moves, each the first legal plain move that leaves a position this helper has not
     * produced yet and gives the opponent nothing to capture. Fails if there is no such move.
     */
    public static void playQuietPlies(Draughts game, int plies) throws Exception {
        Set<String> seen = new HashSet<>();
        seen.add(game.position().key());
        for (int i = 0; i < plies; i++) {
            PlayerId seat = game.currentSeat();
            List<DraughtsMove> legal = game.legalMoves(seat);
            DraughtsMove chosen = null;
            for (DraughtsMove candidate : legal) {
                if (candidate.isCapture()) {
                    throw new AssertionError("A capture is compulsory in " + game.position());
                }
                DraughtsPosition next = GENERATOR.apply(game.position(), candidate);
                List<DraughtsMove> reply = GENERATOR.legalMoves(next);
                boolean exposes = !reply.isEmpty() && reply.get(0).isCapture();
                if (!exposes && !seen.contains(next.key())) {
                    chosen = candidate;
                    seen.add(next.key());
                    break;
                }
            }
            if (chosen == null) {
                throw new AssertionError("No quiet move left at ply " + i + " in " + game.position());
            }
            game.move(seat, chosen.from(), chosen.to(), chosen.captures());
        }
    }
}
