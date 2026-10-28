package org.kevinkib.cardgames.ninetynine.domain.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNineOptions;
import org.kevinkib.cardgames.ninetynine.domain.Phase;
import org.kevinkib.cardgames.ninetynine.domain.RandomDeckSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Opt-in strength benchmark, not run by default: {@code mvn test -Dninetynine.bench=true -Dtest=NinetyNineBotStrengthBenchmark}.
 * Plays full games of four bots (seats rotated game after game so the seat order favours nobody) and prints the
 * share of games won by each level. A fair table gives 25 % a seat.
 */
@EnabledIfSystemProperty(named = "ninetynine.bench", matches = "true")
class NinetyNineBotStrengthBenchmark {

    private static final int GAMES = Integer.getInteger("ninetynine.bench.games", 200);

    @Test
    void hardAgainstMediumAndEasy() {
        report("1 hard vs 3 medium", NinetyNineBotLevel.HARD, NinetyNineBotLevel.MEDIUM);
        report("1 hard vs 3 easy", NinetyNineBotLevel.HARD, NinetyNineBotLevel.EASY);
        report("1 medium vs 3 easy", NinetyNineBotLevel.MEDIUM, NinetyNineBotLevel.EASY);
        report("1 hard vs 1 medium vs 2 easy", NinetyNineBotLevel.HARD, NinetyNineBotLevel.MEDIUM,
                NinetyNineBotLevel.EASY, NinetyNineBotLevel.EASY);
    }

    private void report(String name, NinetyNineBotLevel focus, NinetyNineBotLevel... others) {
        List<NinetyNineBotLevel> levels = new ArrayList<>();
        levels.add(focus);
        if (others.length == 1) {
            for (int i = 0; i < 3; i++) {
                levels.add(others[0]);
            }
        } else {
            levels.addAll(List.of(others));
        }
        Random random = new Random(2026);
        int[] wins = new int[levels.size()];
        long start = System.nanoTime();
        for (int game = 0; game < GAMES; game++) {
            int shift = game % levels.size();
            List<NinetyNineBotStrategy> strategies = new ArrayList<>();
            for (int seat = 0; seat < levels.size(); seat++) {
                strategies.add(NinetyNineBots.forLevel(levels.get((seat + shift) % levels.size()), random));
            }
            int winnerSeat = play(strategies);
            wins[(winnerSeat + shift) % levels.size()]++;
        }
        StringBuilder line = new StringBuilder(name + " (" + GAMES + " games, "
                + (System.nanoTime() - start) / 1_000_000 + " ms):");
        for (int i = 0; i < levels.size(); i++) {
            line.append(String.format(" %s#%d=%.1f%%", levels.get(i).key(), i, 100.0 * wins[i] / GAMES));
        }
        System.out.println("[ninetynine.bench] " + line);
    }

    /** Plays one game to its end and returns the index of the winner's seat. */
    private int play(List<NinetyNineBotStrategy> strategies) {
        NinetyNine game = new NinetyNine(GameId.generate(), strategies.size(), new RandomDeckSource(),
                NinetyNineOptions.defaults());
        while (game.phase() != Phase.FINISHED) {
            try {
                if (game.phase() == Phase.ROUND_OVER) {
                    game.nextRound();
                    continue;
                }
                PlayerId seat = game.currentSeat();
                NinetyNineDecision.Play play = (NinetyNineDecision.Play) strategies.get(seat.getId())
                        .decide(viewOf(game, seat));
                game.play(seat, play.card(), play.aceValue());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        return game.result().orElseThrow().places().entrySet().stream()
                .filter(entry -> entry.getValue() == 1).findFirst().orElseThrow().getKey().getId();
    }

    private static NinetyNineBotView viewOf(NinetyNine game, PlayerId seat) {
        Map<PlayerId, Integer> handSizes = game.handSizes();
        return new NinetyNineBotView(seat, game.version(), game.round(), game.phase(), game.currentSeat(),
                game.total(), game.direction(), game.hand(seat), game.pileSinceReshuffle(), handSizes,
                game.lives(), game.liveSeats(), game.stockSize(), game.options());
    }
}
