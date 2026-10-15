package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.event.TrickWon;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * The owner's rule (spec 3.1, decided 2026-10-15): the first player out closes the trick at once and the next
 * seat with cards leads; a later finisher lets the trick go on, the others may still beat that last play.
 *
 * <p>Each row is a scripted game: the hands (one per seat, separated by {@code ;}), the seat that leads, then the
 * commands ({@code seat:cards} with {@code +} between cards, or {@code seat:pass}), and what the last command
 * left: the reason the trick closed ({@code NONE} when it did not), the seat to act, and the plays on the
 * current trick.
 */
class TrickAfterGoingOutTest {

    @ParameterizedTest(name = "{0}: [{3}] -> closed by {4}, seat {5} to act, {6} play(s) on the trick")
    @CsvSource(delimiter = '|', value = {
            // the first player out, on their own lead: the trick closes, the next seat with cards leads
            "first out leading            | 4C; 5C 6C; 7C 8C; 9C 10C | 0 | 0:4C                      | FIRST_OUT  | 1 | 0",
            // the first player out beating another seat's play: the others never get to answer
            "first out beating a play     | 4C 9C; 6C; 7C 8C; 9D 10D | 0 | 0:4C 1:6C                 | FIRST_OUT  | 2 | 0",
            // the first out is the last seat of the table: the lead wraps to seat 0
            "first out on the last seat   | 4C 9C; 5C 8C; 6C 7C; JC   | 0 | 0:4C 1:5C 2:6C 3:JC       | FIRST_OUT  | 0 | 0",
            // the seat after the first finisher leads, whoever held the trick before
            "next seat leads        | 4C 9C; 5C; 7C 8C; 9D 10D | 0 | 0:4C 1:5C               | FIRST_OUT  | 2 | 0",
            // three players: the first out still closes the trick
            "first out at three           | 4C; 5C 6C; 7C 8C         | 0 | 0:4C                      | FIRST_OUT  | 1 | 0",
            // a later finisher does not close the trick: the others may still beat it
            "second out does not close    | 4C; 5C; 7C 8C; 9D 10D    | 0 | 0:4C 1:5C                 | NONE       | 2 | 1",
            // ... and when the others pass, the trick closes on that play and goes to the next seat with cards
            "second out then all pass     | 4C; 5C; 7C 8C; 9D 10D    | 0 | 0:4C 1:5C 2:pass 3:pass   | ALL_PASSED | 2 | 0",
            // ... or another seat beats it and the trick goes on
            "second out then beaten       | 4C; 5C; 7C 8C; 9D 10D    | 0 | 0:4C 1:5C 2:8C            | NONE       | 3 | 2",
            // a later finisher who is the top of a trick that every other seat passes: the lead wraps to seat 3
            "third out then all pass      | 4C; 5C; 6C; 7C 8C 9C; JC JD | 0 | 0:4C 1:5C 2:6C 3:pass 4:pass | ALL_PASSED | 3 | 0",
    })
    void whoGoesOutDecidesWhetherTheTrickCloses(String name, String hands, int leader, String script,
                                                String expectedClosedBy, int expectedSeat, int expectedPlays)
            throws Exception {
        PresidentBuilder builder = aPresident().leader(leader);
        for (String hand : hands.split(";")) {
            builder.withHand(hand.trim());
        }
        President game = builder.build();

        List<PresidentEvent> last = List.of();
        for (String command : script.trim().split("\\s+")) {
            last = run(game, command);
        }

        List<String> closes = new ArrayList<>();
        for (PresidentEvent event : last) {
            if (event instanceof TrickWon won) {
                closes.add(won.reason().name());
            }
        }
        assertThat(closes.isEmpty() ? "NONE" : closes.get(0), is(expectedClosedBy));
        assertThat(game.currentSeat(), is(new PlayerId(expectedSeat)));
        assertThat(game.trick().plays().size(), is(expectedPlays));
    }

    private static List<PresidentEvent> run(President game, String command) throws Exception {
        String[] parts = command.split(":");
        PlayerId seat = new PlayerId(Integer.parseInt(parts[0]));
        if (parts[1].equals("pass")) {
            return game.pass(seat);
        }
        return game.play(seat, cards(parts[1].replace('+', ' ')));
    }
}
