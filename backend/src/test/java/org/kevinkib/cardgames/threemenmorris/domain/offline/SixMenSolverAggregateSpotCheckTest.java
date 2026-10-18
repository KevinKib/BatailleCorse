package org.kevinkib.cardgames.threemenmorris.domain.offline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.Move;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisResult;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

/**
 * Opt-in, with the property of the solver run ({@code -Dmorris.solveSix=true}): a spot check that the offline
 * solver's move generator ({@link SixMenSolver#successors}) agrees with the aggregate on random positions of
 * random games. For a sampled position, every move (and removal) of the aggregate is replayed on a fresh game
 * and the position it reaches, seen from the next seat, must be exactly one successor of the solver (a multiset
 * comparison); a move that ends the game by reducing the opponent is the solver's terminal successor.
 */
@EnabledIfSystemProperty(named = "morris.solveSix", matches = "true")
class SixMenSolverAggregateSpotCheckTest {

    private static final int GAMES = 60;
    private static final int POSITIONS_PER_GAME = 6;

    private sealed interface Step {
        record Place(int point) implements Step {
        }

        record Slide(int from, int to) implements Step {
        }

        record Take(int point) implements Step {
        }
    }

    private static ThreeMenMorris replay(List<Step> history) throws Exception {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate(), MorrisRules.SIX_MEN, new PlayerId(0));
        for (Step step : history) {
            PlayerId seat = game.currentSeat();
            if (step instanceof Step.Place place) {
                game.place(seat, place.point());
            } else if (step instanceof Step.Slide slide) {
                game.move(seat, slide.from(), slide.to());
            } else {
                game.remove(seat, ((Step.Take) step).point());
            }
        }
        return game;
    }

    /** The position from the seat to move, as the solver sees it. */
    private static SixMenSolver.Pos solverView(ThreeMenMorris game) {
        MorrisPosition position = game.position();
        int mover = position.toMove();
        int me = 0;
        int opp = 0;
        for (int point = 0; point < position.board().size(); point++) {
            Integer owner = position.board().get(point);
            if (owner != null && owner == mover) {
                me |= 1 << point;
            } else if (owner != null) {
                opp |= 1 << point;
            }
        }
        return new SixMenSolver.Pos(me, opp, position.inHand().get(mover), position.inHand().get(1 - mover));
    }

    /** What the aggregate offers at the position the history leads to: one entry per (move, removal) pair. */
    private static List<String> aggregateSuccessors(List<Step> history) throws Exception {
        ThreeMenMorris at = replay(history);
        PlayerId seat = at.currentSeat();
        List<Step> moves = new ArrayList<>();
        at.legalPlacements(seat).forEach(point -> moves.add(new Step.Place(point)));
        for (Move move : at.legalMoves(seat)) {
            moves.add(new Step.Slide(move.from(), move.to()));
        }
        List<String> successors = new ArrayList<>();
        for (Step move : moves) {
            List<Step> afterMove = new ArrayList<>(history);
            afterMove.add(move);
            ThreeMenMorris played = replay(afterMove);
            if (played.pendingRemoval() == null) {
                successors.add(describe(played));
                continue;
            }
            for (int point : played.legalRemovals(seat)) {
                List<Step> afterRemoval = new ArrayList<>(afterMove);
                afterRemoval.add(new Step.Take(point));
                successors.add(describe(replay(afterRemoval)));
            }
        }
        return successors.stream().sorted().toList();
    }

    private static String describe(ThreeMenMorris game) {
        boolean reduced = game.isFinished() && game.result().reason() == MorrisResult.Reason.REDUCED;
        return reduced ? "terminal" : solverView(game).toString();
    }

    @Test
    void theSolversMoveGeneratorAgreesWithTheAggregateOnRandomPositions() throws Exception {
        SixMenSolver solver = new SixMenSolver(MorrisRules.SIX_MEN);
        Random random = new Random(20_261_019L);
        int checked = 0;
        for (int index = 0; index < GAMES; index++) {
            ThreeMenMorris game = new ThreeMenMorris(GameId.generate(), MorrisRules.SIX_MEN, new PlayerId(0));
            List<Step> history = new ArrayList<>();
            while (!game.isFinished()) {
                PlayerId seat = game.currentSeat();
                if (game.pendingRemoval() == null && random.nextInt(8) == 0 && checked < GAMES * POSITIONS_PER_GAME) {
                    List<String> expected = aggregateSuccessors(history);
                    List<String> actual = solver.successors(solverView(game)).stream()
                            .map(succ -> succ.next() == null ? "terminal" : succ.next().toString())
                            .sorted().toList();
                    assertThat("position " + game.position() + " after " + history, actual, is(expected));
                    checked++;
                }
                if (!game.legalRemovals(seat).isEmpty()) {
                    int point = game.legalRemovals(seat).get(random.nextInt(game.legalRemovals(seat).size()));
                    game.remove(seat, point);
                    history.add(new Step.Take(point));
                } else if (!game.legalPlacements(seat).isEmpty()) {
                    int point = game.legalPlacements(seat).get(random.nextInt(game.legalPlacements(seat).size()));
                    game.place(seat, point);
                    history.add(new Step.Place(point));
                } else {
                    Move move = game.legalMoves(seat).get(random.nextInt(game.legalMoves(seat).size()));
                    game.move(seat, move.from(), move.to());
                    history.add(new Step.Slide(move.from(), move.to()));
                }
            }
        }
        System.out.println("[spot-check] solver generator agrees with the aggregate on " + checked + " positions");
        assertThat(checked, greaterThan(100));
    }
}
