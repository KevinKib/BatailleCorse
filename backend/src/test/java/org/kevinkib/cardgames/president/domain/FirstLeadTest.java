package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.rules.FirstLead;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.card;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

class FirstLeadTest {

    /** A deal where {@code target} lands on {@code seat} (round 1: seat 0 receives the first card). */
    private static President dealing(Card target, int seat, int players, FirstLead firstLead, long seed) {
        List<Card> leading = new ArrayList<>(cards("4D 5D 6D 7D 8D 9D 10D").subList(0, seat));
        leading.add(target);
        ScriptedDeckSource source = new ScriptedDeckSource(new RandomDeckSource());
        GameId id = GameId.generate();
        source.fix(id, 1, leading);
        return new President(id, players, seed, source, PresidentOptions.defaults().withFirstLead(firstLead));
    }

    @ParameterizedTest(name = "3 of clubs on seat {0} of {1}")
    @CsvSource({"0, 3", "1, 3", "2, 3", "0, 5", "3, 5", "4, 5", "7, 8"})
    void lowestClubGivesTheLeadToTheHolderOfTheThreeOfClubs(int seat, int players) {
        President game = dealing(card("3C"), seat, players, FirstLead.LOWEST_CLUB, 1L);

        assertThat(game.currentSeat(), is(new PlayerId(seat)));
        assertThat(game.hand(new PlayerId(seat)).contains(card("3C")), is(true));
    }

    @ParameterizedTest(name = "queen of hearts on seat {0} of {1}")
    @CsvSource({"0, 4", "2, 4", "3, 4", "5, 6"})
    void queenOfHeartsGivesTheLeadToItsHolder(int seat, int players) {
        President game = dealing(card("QH"), seat, players, FirstLead.QUEEN_OF_HEARTS, 1L);

        assertThat(game.currentSeat(), is(new PlayerId(seat)));
    }

    @Test
    void randomPicksASeatFromTheSeedWhateverTheCards() {
        Set<PlayerId> seen = new HashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            President first = new President(GameId.generate(), 5, seed, new RandomDeckSource(),
                    PresidentOptions.defaults().withFirstLead(FirstLead.RANDOM));
            President again = new President(GameId.generate(), 5, seed, new RandomDeckSource(),
                    PresidentOptions.defaults().withFirstLead(FirstLead.RANDOM));

            assertThat(first.currentSeat(), is(again.currentSeat()));
            seen.add(first.currentSeat());
        }
        assertThat(seen.size(), is(5));
    }
}
