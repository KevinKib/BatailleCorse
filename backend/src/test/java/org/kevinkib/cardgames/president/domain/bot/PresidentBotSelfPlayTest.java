package org.kevinkib.cardgames.president.domain.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.RandomDeckSource;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.domain.rules.EqualPlay;
import org.kevinkib.cardgames.president.domain.rules.ForbiddenFinish;
import org.kevinkib.cardgames.president.domain.rules.PresidentException;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.domain.rules.TwoCuts;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

/**
 * Seeded matches played by bots only, under the Classic defaults and with equal play off: every match ends, the
 * 52 cards are always accounted for, and the game never refuses a bot's command. With equal play on the bots
 * also answer the binding and take completions out of turn.
 */
class PresidentBotSelfPlayTest {

    private static final int MAX_COMMANDS = 20_000;
    private static final PresidentBotLevel[] MIX = PresidentBotLevel.values();

    /** Plays one match; the bots' levels are given per seat. */
    private static final class Match {
        final President game;
        final List<HeuristicPresidentBot> bots = new ArrayList<>();
        final List<PresidentBotMemory> memories = new ArrayList<>();
        int commands;
        int rounds;
        int outOfTurnCompletions;

        Match(int seats, long seed, List<PresidentBotLevel> levels) {
            this(seats, seed, levels, EqualPlay.ON);
        }

        Match(int seats, long seed, List<PresidentBotLevel> levels, EqualPlay equalPlay) {
            this(seats, seed, levels, PresidentOptions.defaults().withEqualPlay(equalPlay));
        }

        Match(int seats, long seed, List<PresidentBotLevel> levels, PresidentOptions options) {
            game = new President(GameId.generate(), seats, seed, new RandomDeckSource(), options);
            for (int seat = 0; seat < seats; seat++) {
                bots.add(new HeuristicPresidentBot(levels.get(seat).tuning(), new Random(seed * 31 + seat)));
                memories.add(new PresidentBotMemory(new PlayerId(seat)));
            }
        }

        boolean run() throws PresidentException {
            while (!game.isFinished() && commands < MAX_COMMANDS) {
                if (!completeOutOfTurn()) {
                    PlayerId actor = actor();
                    memories.get(actor.id()).observe(PresidentBotView.forSeat(game, actor));
                    PresidentBotDecision decision = bots.get(actor.id())
                            .decide(PresidentBotView.forSeat(game, actor), memories.get(actor.id()));
                    apply(actor, decision);
                }
                commands++;
                for (PlayerId seat : game.getPlayerIds()) {
                    memories.get(seat.id()).observe(PresidentBotView.forSeat(game, seat));
                }
                assertThat("the whole deck after command " + commands, cardsInGame(), is(52));
            }
            return game.isFinished();
        }

        /** A bot that is not on turn and holds a completion decides first; true when it completed. */
        private boolean completeOutOfTurn() throws PresidentException {
            if (game.phase() != Phase.PLAYING) {
                return false;
            }
            for (PlayerId seat : game.getPlayerIds()) {
                if (seat.equals(game.currentSeat())) {
                    continue;
                }
                PresidentBotView view = PresidentBotView.forSeat(game, seat);
                if (!view.can(Action.COMPLETE)) {
                    continue;
                }
                PresidentBotDecision decision = bots.get(seat.id()).decide(view, memories.get(seat.id()));
                if (decision instanceof PresidentBotDecision.Complete complete) {
                    game.play(seat, complete.cards());
                    outOfTurnCompletions++;
                    return true;
                }
                assertThat("an out-of-turn bot only completes or keeps", decision,
                        instanceOf(PresidentBotDecision.Keep.class));
            }
            return false;
        }

        private PlayerId actor() {
            return switch (game.phase()) {
                case PLAYING -> game.currentSeat();
                case EXCHANGE -> game.pendingReturns().get(0).high();
                case ROUND_OVER -> game.getPlayerIds().stream()
                        .filter(seat -> !game.readySeats().contains(seat)).findFirst().orElseThrow();
                case FINISHED -> throw new IllegalStateException("finished");
            };
        }

        private void apply(PlayerId actor, PresidentBotDecision decision) throws PresidentException {
            if (decision instanceof PresidentBotDecision.Play play) {
                game.play(actor, play.cards());
            } else if (decision instanceof PresidentBotDecision.Complete complete) {
                game.play(actor, complete.cards());
            } else if (decision instanceof PresidentBotDecision.Pass) {
                game.pass(actor);
            } else if (decision instanceof PresidentBotDecision.GiveBack giveBack) {
                game.giveBack(actor, giveBack.cards());
            } else if (decision instanceof PresidentBotDecision.Keep) {
                throw new AssertionError("the seat on turn must act: " + actor);
            } else {
                int before = game.round();
                game.ready(actor);
                if (game.round() != before) {
                    rounds++;
                }
            }
        }

        int cardsInGame() {
            Set<Card> all = new HashSet<>(game.discard());
            int count = game.discard().size();
            for (int seat = 0; seat < game.handSizes().size(); seat++) {
                all.addAll(game.hand(new PlayerId(seat)));
                count += game.hand(new PlayerId(seat)).size();
            }
            for (var trickPlay : game.trick().plays()) {
                all.addAll(trickPlay.play().cards());
                count += trickPlay.play().count();
            }
            return all.size() == count ? count : -1;
        }
    }

    private static List<PresidentBotLevel> mixed(int seats, long offset) {
        List<PresidentBotLevel> levels = new ArrayList<>();
        for (int seat = 0; seat < seats; seat++) {
            levels.add(MIX[(int) ((seat + offset) % MIX.length)]);
        }
        return levels;
    }

    private int playMatches(int seats, EqualPlay equalPlay) throws PresidentException {
        int completions = 0;
        for (int index = 0; index < 8; index++) {
            long seed = 1000L * seats + index;
            Match match = new Match(seats, seed, mixed(seats, index), equalPlay);

            boolean finished = match.run();

            assertThat("match " + index + " with " + seats + " bots finished", finished, is(true));
            assertThat(match.commands, lessThan(MAX_COMMANDS));
            assertThat(match.rounds, greaterThan(0));
            completions += match.outOfTurnCompletions;
        }
        return completions;
    }

    @ParameterizedTest
    @EnumSource(EqualPlay.class)
    void threeBotsFinishTheMatch(EqualPlay equalPlay) throws PresidentException {
        playMatches(3, equalPlay);
    }

    @ParameterizedTest
    @EnumSource(EqualPlay.class)
    void fiveBotsFinishTheMatch(EqualPlay equalPlay) throws PresidentException {
        playMatches(5, equalPlay);
    }

    @ParameterizedTest
    @EnumSource(EqualPlay.class)
    void eightBotsFinishTheMatch(EqualPlay equalPlay) throws PresidentException {
        playMatches(8, equalPlay);
    }

    static Stream<Arguments> variants() {
        PresidentOptions d = PresidentOptions.defaults();
        return Stream.of(
                Arguments.of("twoCuts single", d.withTwoCuts(TwoCuts.SINGLE)),
                Arguments.of("twoCuts ladder", d.withTwoCuts(TwoCuts.LADDER)),
                Arguments.of("quadClears off", d.withQuadClears(false)),
                Arguments.of("forbiddenFinish off", d.withForbiddenFinish(ForbiddenFinish.OFF)),
                Arguments.of("mustPlay on", d.withMustPlay(true)),
                Arguments.of("everything on", d.withTwoCuts(TwoCuts.LADDER).withMustPlay(true)
                        .withQuadClears(false)));
    }

    /** Each trick variant on its own, then together: the bots finish the match and the game refuses nothing. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("variants")
    void botsFinishMatchesWithEachTrickVariant(String name, PresidentOptions options) throws PresidentException {
        for (int seats : new int[] {3, 5, 8}) {
            for (int index = 0; index < 4; index++) {
                Match match = new Match(seats, 7000L + 100L * seats + index, mixed(seats, index), options);

                boolean finished = match.run();

                assertThat(name + " with " + seats + " bots, match " + index, finished, is(true));
                assertThat(match.commands, lessThan(MAX_COMMANDS));
            }
        }
    }

    @Test
    void withEqualPlayOnTheBotsTakeCompletionsOutOfTurnAndWithItOffNever() throws PresidentException {
        int on = playMatches(5, EqualPlay.ON) + playMatches(8, EqualPlay.ON) + playMatches(3, EqualPlay.ON);
        int off = playMatches(5, EqualPlay.OFF);

        assertThat(on, greaterThan(0));
        assertThat(off, is(0));
    }

    @Test
    void theSameSeedPlaysTheSameMatch() throws PresidentException {
        Match first = new Match(5, 77L, mixed(5, 0));
        Match second = new Match(5, 77L, mixed(5, 0));

        first.run();
        second.run();

        assertThat(second.commands, is(first.commands));
        assertThat(second.game.standings(), is(first.game.standings()));
    }

    @Test
    void aHardBotOutscoresAnEasyBotOverManyMatches() throws PresidentException {
        Map<PresidentBotLevel, Integer> points = new EnumMap<>(PresidentBotLevel.class);
        for (int index = 0; index < 60; index++) {
            List<PresidentBotLevel> levels = new ArrayList<>(List.of(PresidentBotLevel.EASY, PresidentBotLevel.MEDIUM,
                    PresidentBotLevel.HARD, PresidentBotLevel.EASY));
            java.util.Collections.rotate(levels, index);
            Match match = new Match(4, 5000L + index, levels);
            match.run();
            for (int seat = 0; seat < 4; seat++) {
                points.merge(levels.get(seat), match.game.standings().points(new PlayerId(seat)), Integer::sum);
            }
        }
        // two easy seats per match: compare per seat
        assertThat("hard beats easy per seat", points.get(PresidentBotLevel.HARD),
                greaterThan(points.get(PresidentBotLevel.EASY) / 2));
    }
}
