package org.kevinkib.cardgames.president.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.rules.PresidentException;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

/**
 * Seeded matches played by bots only, under the default rules (Classic as shipped today): every match ends, the
 * 52 cards are always accounted for, and the game never refuses a bot's command.
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

        Match(int seats, long seed, List<PresidentBotLevel> levels) {
            game = new President(GameId.generate(), seats, seed);
            for (int seat = 0; seat < seats; seat++) {
                bots.add(new HeuristicPresidentBot(levels.get(seat).tuning(), new Random(seed * 31 + seat)));
                memories.add(new PresidentBotMemory(new PlayerId(seat)));
            }
        }

        boolean run() throws PresidentException {
            while (!game.isFinished() && commands < MAX_COMMANDS) {
                PlayerId actor = actor();
                memories.get(actor.id()).observe(PresidentBotView.forSeat(game, actor));
                PresidentBotDecision decision = bots.get(actor.id())
                        .decide(PresidentBotView.forSeat(game, actor), memories.get(actor.id()));
                apply(actor, decision);
                commands++;
                for (PlayerId seat : game.getPlayerIds()) {
                    memories.get(seat.id()).observe(PresidentBotView.forSeat(game, seat));
                }
                assertThat("the whole deck after command " + commands, cardsInGame(), is(52));
            }
            return game.isFinished();
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
            } else if (decision instanceof PresidentBotDecision.Pass) {
                game.pass(actor);
            } else if (decision instanceof PresidentBotDecision.GiveBack giveBack) {
                game.giveBack(actor, giveBack.cards());
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

    private void playMatches(int seats) throws PresidentException {
        for (int index = 0; index < 8; index++) {
            long seed = 1000L * seats + index;
            Match match = new Match(seats, seed, mixed(seats, index));

            boolean finished = match.run();

            assertThat("match " + index + " with " + seats + " bots finished", finished, is(true));
            assertThat(match.commands, lessThan(MAX_COMMANDS));
            assertThat(match.rounds, greaterThan(0));
        }
    }

    @Test
    void threeBotsFinishTheMatch() throws PresidentException {
        playMatches(3);
    }

    @Test
    void fiveBotsFinishTheMatch() throws PresidentException {
        playMatches(5);
    }

    @Test
    void eightBotsFinishTheMatch() throws PresidentException {
        playMatches(8);
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
