package org.kevinkib.cardgames.blackjack.domain.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.kevinkib.cardgames.blackjack.domain.Blackjack;
import org.kevinkib.cardgames.blackjack.domain.BlackjackException;
import org.kevinkib.cardgames.blackjack.domain.BlackjackOptions;
import org.kevinkib.cardgames.blackjack.domain.Phase;
import org.kevinkib.cardgames.blackjack.domain.RandomShoeSource;
import org.kevinkib.cardgames.blackjack.domain.event.BlackjackResult;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Opt-in strength benchmark on the real aggregate, not run by default:
 * {@code mvn test -Dtest=BlackjackBotAggregateBenchmark -Dblackjack.bench=true}.
 *
 * <p>The same scenarios as {@link BlackjackBotStrengthBenchmark} (which runs on a small simulator of the rules), but
 * every hand is played through {@link Blackjack} and every bot decides from {@link BlackjackBotView#forSeat}, as in
 * production: 1,000 tournaments of 4 seats, 10 hands, default options, seats rotated; a tie for first splits the win.
 */
@EnabledIfSystemProperty(named = "blackjack.bench", matches = "true")
class BlackjackBotAggregateBenchmark {

    private static final int TOURNAMENTS = Integer.getInteger("blackjack.bench.tournaments", 1000);
    private static final int SEATS = 4;

    @Test
    void winShareOfEachLevelOnTheAggregate() {
        report("1 hard vs 3 medium", BlackjackBotLevel.HARD, BlackjackBotLevel.MEDIUM, BlackjackBotLevel.MEDIUM,
                BlackjackBotLevel.MEDIUM);
        report("1 medium vs 3 easy", BlackjackBotLevel.MEDIUM, BlackjackBotLevel.EASY, BlackjackBotLevel.EASY,
                BlackjackBotLevel.EASY);
        report("hard + medium + 2 easy", BlackjackBotLevel.HARD, BlackjackBotLevel.MEDIUM, BlackjackBotLevel.EASY,
                BlackjackBotLevel.EASY);
        report("4 medium (control)", BlackjackBotLevel.MEDIUM, BlackjackBotLevel.MEDIUM, BlackjackBotLevel.MEDIUM,
                BlackjackBotLevel.MEDIUM);
    }

    private void report(String name, BlackjackBotLevel... levels) {
        Random random = new Random(2026);
        Map<BlackjackBotLevel, double[]> wins = new EnumMap<>(BlackjackBotLevel.class);
        for (int t = 0; t < TOURNAMENTS; t++) {
            int shift = t % SEATS;
            List<BlackjackBotLevel> table = new ArrayList<>();
            for (int seat = 0; seat < SEATS; seat++) {
                table.add(levels[(seat + shift) % SEATS]);
            }
            double[] share = playTournament(table, random);
            for (int seat = 0; seat < SEATS; seat++) {
                double[] sum = wins.computeIfAbsent(table.get(seat), level -> new double[2]);
                sum[0] += share[seat];
                sum[1]++;
            }
        }
        StringBuilder line = new StringBuilder(String.format("%-26s", name));
        for (Map.Entry<BlackjackBotLevel, double[]> entry : wins.entrySet()) {
            double[] sum = entry.getValue();
            line.append(String.format("  %s %.1f%% per seat", entry.getKey().key(), 100 * sum[0] / sum[1]));
        }
        System.out.println("AGGREGATE " + line);
    }

    /** The share of the tournament each seat wins: the seats on place 1 split it. */
    private double[] playTournament(List<BlackjackBotLevel> levels, Random random) {
        Blackjack game = new Blackjack(new GameId(UUID.randomUUID().toString()), levels.size(), new RandomShoeSource(),
                BlackjackOptions.defaults());
        List<BlackjackBotStrategy> bots = new ArrayList<>();
        for (BlackjackBotLevel level : levels) {
            bots.add(BlackjackBots.forLevel(level, random));
        }
        try {
            while (!game.isFinished()) {
                if (game.phase() == Phase.BETTING) {
                    for (int i = 0; i < levels.size(); i++) {
                        PlayerId seat = new PlayerId(i);
                        if (!game.seat(seat).eliminated() && !game.seat(seat).betPlaced()) {
                            game.bet(seat, bots.get(i).decideBet(BlackjackBotView.forSeat(game, seat)));
                        }
                    }
                } else {
                    PlayerId seat = game.currentSeat();
                    BlackjackBotView view = BlackjackBotView.forSeat(game, seat);
                    switch (bots.get(seat.id()).decidePlay(view)) {
                        case HIT -> game.hit(seat);
                        case STAND -> game.stand(seat);
                        case DOUBLE -> game.doubleDown(seat);
                        case SPLIT -> game.split(seat);
                        default -> throw new IllegalStateException("not a play");
                    }
                }
            }
        } catch (BlackjackException e) {
            throw new IllegalStateException("a bot made a refused move", e);
        }
        BlackjackResult result = game.result().orElseThrow();
        long first = result.places().values().stream().filter(p -> p == 1).count();
        double[] share = new double[levels.size()];
        for (int i = 0; i < levels.size(); i++) {
            Integer place = result.places().get(new PlayerId(i));
            if (place != null && place == 1) {
                share[i] = 1.0 / first;
            }
        }
        return share;
    }
}
