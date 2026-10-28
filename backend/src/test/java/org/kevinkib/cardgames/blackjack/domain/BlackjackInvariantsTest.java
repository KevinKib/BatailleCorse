package org.kevinkib.cardgames.blackjack.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.event.BlackjackEvent;
import org.kevinkib.cardgames.blackjack.domain.event.HandSettled;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Random games from the public API with a seeded generator: random legal and illegal commands, every game ends,
 * an accepted command bumps the version, a refused one changes nothing, no stack is ever negative and the points
 * are conserved (stacks plus the bets in play only move by the settled nets and by the bets a forfeit loses).
 */
class BlackjackInvariantsTest {

    @Test
    void randomGamesConservePointsAndEnd() throws Exception {
        Random random = new Random(7);
        for (int players = 1; players <= 6; players++) {
            for (int run = 0; run < 25; run++) {
                BlackjackOptions options = new BlackjackOptions(BlackjackOptions.Hands.FIVE,
                        run % 2 == 0 ? BlackjackOptions.MaxBet.FIFTY : BlackjackOptions.MaxBet.HUNDRED);
                Blackjack game = new Blackjack(GameId.generate(), players, new RandomShoeSource(), options);
                int expected = players * BlackjackOptions.STARTING_STACK;
                int guard = 0;
                while (!game.isFinished() && guard++ < 5_000) {
                    expected = step(game, random, expected);
                    assertEquals(expected, points(game), "points drifted with " + players + " players");
                    for (PlayerId seat : game.getPlayerIds()) {
                        assertTrue(game.seat(seat).stack() >= 0, "negative stack");
                    }
                }
                assertTrue(game.isFinished(), "game did not end with " + players + " players");
            }
        }
    }

    private static int step(Blackjack game, Random random, int expected) throws Exception {
        int roll = random.nextInt(100);
        int before = game.version();
        String snapshot = snapshot(game);
        List<BlackjackEvent> events;
        try {
            if (roll < 3) {
                PlayerId seat = game.getPlayerIds().get(random.nextInt(game.getPlayerIds().size()));
                int committed = committed(game, seat);
                boolean live = !game.seat(seat).eliminated();
                game.forfeit(seat);
                assertEquals(live ? before + 1 : before, game.version());
                return live ? expected - committed + settledNets(game.lastForfeitEvents()) : expected;
            }
            if (roll < 25) {
                events = noise(game, random);
            } else {
                events = legal(game, random);
            }
        } catch (BlackjackException refused) {
            assertEquals(before, game.version(), "a refused command moved the version");
            assertEquals(snapshot, snapshot(game), "a refused command mutated the game");
            return expected;
        }
        assertEquals(before + 1, game.version(), "an accepted command must bump the version");
        return expected + settledNets(events);
    }

    private static int settledNets(List<BlackjackEvent> events) {
        int total = 0;
        for (BlackjackEvent event : events) {
            if (event instanceof HandSettled settled) {
                total += settled.nets().values().stream().mapToInt(Integer::intValue).sum();
            }
        }
        return total;
    }

    /** A command that is legal for the seat that must act, with a random choice among the available actions. */
    private static List<BlackjackEvent> legal(Blackjack game, Random random) throws BlackjackException {
        if (game.phase() == Phase.BETTING) {
            List<PlayerId> waiting = game.getPlayerIds().stream()
                    .filter(seat -> game.availableActions(seat).contains(Action.BET)).toList();
            PlayerId seat = waiting.get(random.nextInt(waiting.size()));
            return game.bet(seat, 1 + random.nextInt(game.maxBetFor(seat)));
        }
        PlayerId seat = game.currentSeat();
        List<Action> actions = game.availableActions(seat);
        return act(game, seat, actions.get(random.nextInt(actions.size())));
    }

    /** A random command from a random seat: most are refused. */
    private static List<BlackjackEvent> noise(Blackjack game, Random random) throws BlackjackException {
        PlayerId seat = game.getPlayerIds().get(random.nextInt(game.getPlayerIds().size()));
        return switch (random.nextInt(5)) {
            case 0 -> game.bet(seat, random.nextInt(130) - 5);
            case 1 -> act(game, seat, Action.HIT);
            case 2 -> act(game, seat, Action.STAND);
            case 3 -> act(game, seat, Action.DOUBLE);
            default -> act(game, seat, Action.SPLIT);
        };
    }

    private static List<BlackjackEvent> act(Blackjack game, PlayerId seat, Action action) throws BlackjackException {
        return switch (action) {
            case HIT -> game.hit(seat);
            case STAND -> game.stand(seat);
            case DOUBLE -> game.doubleDown(seat);
            case SPLIT -> game.split(seat);
            case BET -> throw new IllegalStateException();
        };
    }

    private static int committed(Blackjack game, PlayerId seat) {
        SeatState state = game.seat(seat);
        return switch (game.phase()) {
            case BETTING -> state.bet();
            case PLAYING -> state.hands().stream().mapToInt(PlayerHand::bet).sum();
            case FINISHED -> 0;
        };
    }

    private static int points(Blackjack game) {
        int total = 0;
        for (PlayerId seat : game.getPlayerIds()) {
            total += game.seat(seat).stack() + committed(game, seat);
        }
        return total;
    }

    private static String snapshot(Blackjack game) {
        List<Object> parts = new ArrayList<>();
        parts.add(game.phase());
        parts.add(game.handNumber());
        parts.add(game.currentSeat());
        parts.add(game.dealerCards());
        game.getPlayerIds().forEach(seat -> parts.add(game.seat(seat)));
        return parts.toString();
    }

    @Test
    void theSnapshotHelperSeesAChange() throws Exception {
        Blackjack game = new Blackjack(GameId.generate(), 2, new RandomShoeSource(), BlackjackOptions.defaults());
        String before = snapshot(game);
        game.bet(new PlayerId(0), 10);
        assertFalse(before.equals(snapshot(game)));
    }
}
