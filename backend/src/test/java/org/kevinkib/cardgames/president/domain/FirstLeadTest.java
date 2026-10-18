package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.card;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/** The king of hearts always leads round 1 (a fixed rule, with the default options). */
class FirstLeadTest {

    /** A deal where the king of hearts lands on {@code seat} (round 1: seat 0 receives the first card). */
    private static President dealing(int seat, int players, long seed) {
        List<Card> leading = new ArrayList<>(cards("4D 5D 6D 7D 8D 9D 10D").subList(0, seat));
        leading.add(card("KH"));
        ScriptedDeckSource source = new ScriptedDeckSource(new RandomDeckSource());
        GameId id = GameId.generate();
        source.fix(id, 1, leading);
        return new President(id, players, seed, source, org.kevinkib.cardgames.president.domain.rules.PresidentOptions.defaults());
    }

    @ParameterizedTest(name = "king of hearts on seat {0} of {1}")
    @CsvSource({"0, 3", "1, 3", "2, 3", "0, 5", "3, 5", "4, 5", "7, 8"})
    void kingOfHeartsGivesTheLeadToItsHolder(int seat, int players) {
        President game = dealing(seat, players, 1L);

        assertThat(game.currentSeat(), is(new PlayerId(seat)));
        assertThat(game.hand(new PlayerId(seat)).contains(card("KH")), is(true));
    }

    @Test
    void theHolderOfTheKingOfHeartsLeadsWithAnyPlayAndIsNotObligedToIncludeIt() throws Exception {
        President game = dealing(2, 4, 1L);
        PlayerId leader = game.currentSeat();
        Card other = game.hand(leader).stream().filter(c -> !c.equals(card("KH"))).findFirst().orElseThrow();

        game.play(leader, List.of(other));

        assertThat(game.hand(leader).contains(card("KH")), is(true));
        assertThat(game.trick().plays().size(), is(1));
    }
}
