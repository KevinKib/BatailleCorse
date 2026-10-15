package org.kevinkib.cardgames.president.domain;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.exchange.PendingReturn;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.IntConsumer;

/** Drives a President match with random legal commands, whatever the phase. */
final class PresidentDriver {

    private PresidentDriver() {
    }

    /** The cards in play: hands, the trick being played and the tricks set aside. */
    static List<Card> cardsInGame(President game) {
        List<Card> all = new ArrayList<>(game.discard());
        for (int seat = 0; seat < game.handSizes().size(); seat++) {
            all.addAll(game.hand(new PlayerId(seat)));
        }
        for (var trickPlay : game.trick().plays()) {
            all.addAll(trickPlay.play().cards());
        }
        return all;
    }

    static boolean wholeDeck(President game, int size) {
        List<Card> all = cardsInGame(game);
        Set<Card> distinct = new HashSet<>(all);
        return all.size() == size && distinct.size() == size;
    }

    /** One legal command of a random seat that has something to do; false once the match is finished. */
    static boolean step(President game, Random random) throws Exception {
        switch (game.phase()) {
            case FINISHED -> {
                return false;
            }
            case EXCHANGE -> {
                PendingReturn pending = game.pendingReturns().get(0);
                List<Card> hand = new ArrayList<>(game.hand(pending.high()));
                java.util.Collections.shuffle(hand, random);
                game.giveBack(pending.high(), hand.subList(0, pending.count()));
            }
            case ROUND_OVER -> {
                for (PlayerId seat : game.getPlayerIds()) {
                    if (!game.readySeats().contains(seat)) {
                        game.ready(seat);
                        break;
                    }
                }
            }
            case PLAYING -> {
                PlayerId seat = game.currentSeat();
                List<Play> options = game.legalPlays(seat);
                boolean leading = game.trick().plays().isEmpty();
                if (!leading && (options.isEmpty() || random.nextInt(4) == 0)) {
                    game.pass(seat);
                } else {
                    game.play(seat, options.get(random.nextInt(options.size())).cards());
                }
            }
        }
        return true;
    }

    /** Plays until the match is finished (or the step budget runs out), checking the deck after each command. */
    static int playMatch(President game, Random random, int deckSize, IntConsumer afterStep) throws Exception {
        int steps = 0;
        while (steps < 20000 && step(game, random)) {
            steps++;
            if (!wholeDeck(game, deckSize)) {
                throw new AssertionError("the deck is not whole after step " + steps + " in phase " + game.phase());
            }
            afterStep.accept(steps);
        }
        return steps;
    }

    static boolean inPlay(President game) {
        return game.phase() == Phase.PLAYING;
    }
}
