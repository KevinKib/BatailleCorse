package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.domain.rules.AfterPass;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Invariant: seeded games driven by a random legal-play driver never lose or duplicate a card (hands +
 * trick + set-aside tricks are always the whole deck), never refuse a legal command and always end.
 */
class PresidentRandomGamesTest {

    private static int cardsInGame(President game) {
        int count = game.discard().size();
        for (PlayerId seat : game.getPlayerIds()) {
            count += game.hand(seat).size();
        }
        for (var trickPlay : game.trick().plays()) {
            count += trickPlay.play().count();
        }
        return count;
    }

    private static Set<Card> distinctCards(President game) {
        Set<Card> all = new HashSet<>(game.discard());
        for (PlayerId seat : game.getPlayerIds()) {
            all.addAll(game.hand(seat));
        }
        for (var trickPlay : game.trick().plays()) {
            all.addAll(trickPlay.play().cards());
        }
        return all;
    }

    private static void playToTheEnd(President game, Random random) throws Exception {
        for (int step = 0; step < 5000 && game.phase() == Phase.PLAYING; step++) {
            PlayerId seat = game.currentSeat();
            List<Play> options = game.legalPlays(seat);
            boolean leading = game.trick().plays().isEmpty();
            boolean pass = !leading && (options.isEmpty() || random.nextInt(4) == 0);
            if (pass) {
                game.pass(seat);
            } else {
                game.play(seat, options.get(random.nextInt(options.size())).cards());
            }
            assertThat("cards after step " + step, cardsInGame(game), is(52));
        }
        if (game.phase() == Phase.PLAYING) {
            fail("the game did not end");
        }
    }

    @ParameterizedTest
    @EnumSource(AfterPass.class)
    void seededGamesKeepTheWholeDeckAndEnd(AfterPass afterPass) throws Exception {
        for (int players = 3; players <= 8; players++) {
            for (long seed = 0; seed < 25; seed++) {
                President game = new President(GameId.generate(), players, seed, new RandomDeckSource(),
                        PresidentOptions.defaults().withAfterPass(afterPass));
                assertThat(distinctCards(game).size(), is(52));

                playToTheEnd(game, new Random(seed * 31 + players));

                assertThat(game.phase(), is(Phase.ROUND_OVER));
                assertThat(new HashSet<>(game.finishingOrder()).size(), is(players));
                assertThat(distinctCards(game).size(), is(52));
                long withCards = game.getPlayerIds().stream().filter(s -> !game.hand(s).isEmpty()).count();
                assertThat(withCards, is(1L));
            }
        }
    }

    @Test
    void theLastPlayerKeepsTheirCardsAndEveryoneElseEmptiedTheirHand() throws Exception {
        President game = new President(GameId.generate(), 5, 99L);

        playToTheEnd(game, new Random(5));

        List<PlayerId> order = new ArrayList<>(game.finishingOrder());
        PlayerId last = order.remove(order.size() - 1);
        for (PlayerId seat : order) {
            assertThat(game.hand(seat).isEmpty(), is(true));
        }
        assertThat(game.hand(last).isEmpty(), is(false));
    }
}
