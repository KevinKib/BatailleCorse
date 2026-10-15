package org.kevinkib.cardgames.president.domain.exchange;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TitlesTest {

    /** One row per player count of the spec table (3.1): titles from 1st to last place. */
    @ParameterizedTest(name = "{0} players")
    @CsvSource(delimiter = '|', value = {
            "3 | PRESIDENT NEUTRAL SCUM",
            "4 | PRESIDENT VICE_PRESIDENT VICE_SCUM SCUM",
            "5 | PRESIDENT VICE_PRESIDENT NEUTRAL VICE_SCUM SCUM",
            "6 | PRESIDENT VICE_PRESIDENT NEUTRAL NEUTRAL VICE_SCUM SCUM",
            "8 | PRESIDENT VICE_PRESIDENT NEUTRAL NEUTRAL NEUTRAL NEUTRAL VICE_SCUM SCUM",
    })
    void titlesByPlace(int players, String expected) {
        List<Title> titles = Arrays.stream(expected.split(" ")).map(Title::valueOf).toList();

        assertThat(Titles.forPlaces(players), is(titles));
    }

    @Test
    void fewerThanThreePlayersHaveNoTitles() {
        assertThrows(IllegalArgumentException.class, () -> Titles.forPlaces(2));
    }

    /** Two players are left only after a forfeit: a President and a Scum, no middle. */
    @ParameterizedTest(name = "{0} active players")
    @CsvSource(delimiter = '|', value = {
            "2 | PRESIDENT SCUM",
            "3 | PRESIDENT NEUTRAL SCUM",
            "5 | PRESIDENT VICE_PRESIDENT NEUTRAL VICE_SCUM SCUM",
    })
    void activePlayersAfterAForfeit(int players, String expected) {
        List<Title> titles = Arrays.stream(expected.split(" ")).map(Title::valueOf).toList();

        assertThat(Titles.forActivePlayers(players), is(titles));
    }

    @Test
    void fewerThanTwoActivePlayersHaveNoTitles() {
        assertThrows(IllegalArgumentException.class, () -> Titles.forActivePlayers(1));
    }
}
