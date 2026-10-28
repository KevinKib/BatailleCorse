package org.kevinkib.cardgames.kilodemerde.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Random games from the public API: no card is ever lost or duplicated, and every game ends. */
class KiloDeMerdeInvariantsTest {

    @Test
    void randomGamesConserveTheFiftyTwoCardsAndEnd() throws Exception {
        Random random = new Random(42);
        for (int players = 3; players <= 10; players++) {
            for (int run = 0; run < 15; run++) {
                KiloDeMerde game = new KiloDeMerde(GameId.generate(), players, new RandomDeckSource(),
                        new KiloDeMerdeOptions(KiloDeMerdeOptions.Rounds.FIVE));
                int guard = 0;
                while (!game.isFinished() && guard++ < 20_000) {
                    act(game, random);
                    assertConserved(game);
                }
                assertTrue(game.isFinished(), "game did not end with " + players + " players");
            }
        }
    }

    private static void act(KiloDeMerde game, Random random) throws Exception {
        List<PlayerId> active = game.activeSeats();
        PlayerId seat = active.get(random.nextInt(active.size()));
        switch (game.phase()) {
            case PASSING -> {
                int roll = random.nextInt(60);
                if (roll == 0) {
                    game.slap(seat);
                } else if (roll == 1) {
                    game.expirePassTimer(game.step());
                } else if (roll == 2 && active.size() > 3 && random.nextInt(8) == 0) {
                    game.forfeit(seat);
                } else if (!game.hasChosen(seat)) {
                    List<Card> hand = game.hand(seat);
                    game.choosePass(seat, hand.get(random.nextInt(hand.size())));
                }
            }
            case RACE -> {
                if (random.nextInt(12) == 0) {
                    game.closeRace(game.raceId());
                } else if (!game.slapOrder().contains(seat)) {
                    game.slap(seat);
                }
            }
            case ROUND_OVER -> game.nextRound();
            default -> throw new IllegalStateException();
        }
    }

    private static void assertConserved(KiloDeMerde game) {
        if (game.phase() == Phase.FINISHED) {
            return;
        }
        int players = game.activeSeats().size();
        List<Card> all = new ArrayList<>();
        game.activeSeats().forEach(seat -> {
            assertEquals(4, game.hand(seat).size());
            all.addAll(game.kiloCards(seat));
        });
        Set<Card> inHands = new HashSet<>();
        game.activeSeats().forEach(seat -> inHands.addAll(game.hand(seat)));
        assertEquals(new HashSet<>(KiloDeck.handDeck(players)), inHands);
        assertEquals(KiloDeck.kiloPile(players).size(), all.size() + game.pileSize());
        assertEquals(all.size(), new HashSet<>(all).size());
    }
}
