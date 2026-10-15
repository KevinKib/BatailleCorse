package org.kevinkib.cardgames.president.domain.play;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.codes;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.rank;

class PlayParserTest {

    @ParameterizedTest(name = "{0} is a set of {1} {2}")
    @CsvSource(delimiter = '|', value = {
            "3C | 1 | 3",
            "2S | 1 | 2",
            "9C 9H | 2 | 9",
            "KS KD | 2 | K",
            "5C 5D 5H | 3 | 5",
            "AC AD AH | 3 | A",
            "7C 7D 7H 7S | 4 | 7",
            "2C 2D 2H 2S | 4 | 2",
    })
    void validSets(String played, int count, String rank) throws Exception {
        Play play = PlayParser.parse(cards(played));

        assertThat(play.count(), is(count));
        assertThat(play.rank(), is(rank(rank)));
        assertThat(codes(play.cards()), is(played));
    }

    @ParameterizedTest(name = "[{0}] is refused")
    @ValueSource(strings = {"", "3C 4C", "3C 3D 4H", "KC KD AH AS", "2C 3C 4C"})
    void emptyOrMixedRanksAreIllegal(String played) {
        assertThrows(IllegalPlayException.class, () -> PlayParser.parse(cards(played)));
    }

    @Test
    void aJokerIsNotPlayableWithoutTheJokersOption() {
        List<Card> joker = List.of(new Card(FrenchRank.JOKER, FrenchSuit.RED_JOKER));

        assertThrows(IllegalPlayException.class, () -> PlayParser.parse(joker));
    }

    @Test
    void aNullCardOrANullListIsIllegal() {
        List<Card> withNull = new ArrayList<>(Arrays.asList(cards("3C").get(0), null));

        assertThrows(IllegalPlayException.class, () -> PlayParser.parse(withNull));
        assertThrows(IllegalPlayException.class, () -> PlayParser.parse(null));
    }

    @Test
    void theParsedPlayDoesNotShareTheCallersList() throws Exception {
        List<Card> input = new ArrayList<>(cards("5C 5D"));

        Play play = PlayParser.parse(input);
        input.clear();

        assertThat(play.count(), is(2));
    }
}
