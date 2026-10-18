package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.Geometry;
import org.kevinkib.cardgames.threemenmorris.domain.Move;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisResult;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

/**
 * The two statements of the Six Men's Morris rules, the aggregate ({@link ThreeMenMorris}) and the bot
 * transition model ({@link MorrisTransitions}), must agree. 2000 random games with a fixed seed are played
 * through the aggregate; at every position the legal placements, moves and removals, the mills (a pending
 * removal), the positions reached and the endings (reduced, blocked) are compared. The draws by repetition and
 * by quiet plies depend on the history, so they are only allowed on the aggregate side.
 */
class SixMenTransitionsCrossCheckTest {

    private static final int GAMES = 2_000;
    private static final long SEED = 20_261_018L;
    private static final int PLY_BOUND = 2_000;

    private static final MorrisRules FLYING = new MorrisRules(Geometry.SIX_MEN, 6, MorrisRules.LineRule.MILL,
            MorrisRules.RemovalRule.PROTECT_MILLS, MorrisRules.FlyingRule.AT_THREE, 50);

    static Stream<MorrisRules> rules() {
        return Stream.of(MorrisRules.SIX_MEN, FLYING);
    }

    /** The decisions the aggregate offers to the seat to move, as the model's decision type. */
    private static Set<MorrisDecision> aggregateDecisions(ThreeMenMorris game) {
        PlayerId seat = game.currentSeat();
        Set<MorrisDecision> decisions = new HashSet<>();
        game.legalRemovals(seat).forEach(point -> decisions.add(new MorrisDecision.Remove(point)));
        game.legalPlacements(seat).forEach(point -> decisions.add(new MorrisDecision.Place(point)));
        for (Move move : game.legalMoves(seat)) {
            decisions.add(new MorrisDecision.Step(move.from(), move.to()));
        }
        return decisions;
    }

    private static void play(ThreeMenMorris game, MorrisDecision decision) throws Exception {
        PlayerId seat = game.currentSeat();
        if (decision instanceof MorrisDecision.Place place) {
            game.place(seat, place.point());
        } else if (decision instanceof MorrisDecision.Step step) {
            game.move(seat, step.from(), step.to());
        } else {
            game.remove(seat, ((MorrisDecision.Remove) decision).point());
        }
    }

    @ParameterizedTest
    @MethodSource("rules")
    void theAggregateAndTheTransitionsAgreeOnTwoThousandRandomGames(MorrisRules rules) throws Exception {
        Random random = new Random(SEED);
        Map<String, Integer> endings = new TreeMap<>();
        int removals = 0;
        long plies = 0;
        for (int index = 0; index < GAMES; index++) {
            ThreeMenMorris game = new ThreeMenMorris(GameId.generate(), rules, new PlayerId(random.nextInt(2)));
            int ply = 0;
            while (!game.isFinished()) {
                String context = rules.flying() + " game " + index + " ply " + ply;
                assertThat(context + " (bound)", ply++ < PLY_BOUND, is(true));
                MorrisPosition position = game.position();
                List<MorrisDecision> legal = MorrisTransitions.legal(position);

                assertThat(context + " legal decisions of " + position, new HashSet<>(legal),
                        is(aggregateDecisions(game)));
                assertThat(context + " no duplicate decision", legal.size(), is(new HashSet<>(legal).size()));
                assertThat(context + " removal pending", position.removing(), is(game.pendingRemoval() != null));
                assertThat(context + " a legal decision exists", legal.isEmpty(), is(false));
                if (position.removing()) {
                    removals++;
                }

                MorrisDecision decision = legal.get(random.nextInt(legal.size()));
                MorrisTransitions.Outcome outcome = MorrisTransitions.apply(position, decision);
                PlayerId mover = game.currentSeat();
                play(game, decision);
                plies++;

                if (outcome instanceof MorrisTransitions.Outcome.Win win) {
                    assertThat(context + " the model ends the game after " + decision, game.isFinished(), is(true));
                    assertThat(context + " winner", game.result().winner(), is(new PlayerId(win.seat())));
                    assertThat(context + " winner is the mover", win.seat(), is(mover.id()));
                    assertThat(context + " reason " + game.result().reason(),
                            game.result().reason() == MorrisResult.Reason.REDUCED
                                    || game.result().reason() == MorrisResult.Reason.BLOCKED, is(true));
                } else {
                    MorrisTransitions.Outcome.Next next = (MorrisTransitions.Outcome.Next) outcome;
                    assertThat(context + " position after " + decision, game.position(), is(next.position()));
                    if (game.isFinished()) {
                        assertThat(context + " only a draw by history ends the game when the model goes on: "
                                + game.result(), game.result().isDraw(), is(true));
                    }
                }
            }
            endings.merge(game.result().reason().toString(), 1, Integer::sum);
        }
        System.out.println("[cross-check] " + rules.flying() + ": " + GAMES + " games, " + plies + " plies, "
                + removals + " removals, endings " + endings);
        assertThat("some games end reduced", endings.getOrDefault("REDUCED", 0), greaterThan(0));
        assertThat("some games end blocked", endings.getOrDefault("BLOCKED", 0), greaterThan(0));
        assertThat("some games are drawn", endings.getOrDefault("DRAW_MOVE_LIMIT", 0)
                + endings.getOrDefault("DRAW_REPETITION", 0), greaterThan(0));
        assertThat("mills are closed", removals, greaterThan(GAMES));
    }
}
