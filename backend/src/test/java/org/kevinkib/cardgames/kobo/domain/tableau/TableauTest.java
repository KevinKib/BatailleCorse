package org.kevinkib.cardgames.kobo.domain.tableau;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.cards;

class TableauTest {

    private Tableau tableau() {
        return new Tableau(cards("AH", "2H", "3H", "KS"));
    }

    @Test
    void givenFourCards_thenAllSlotsOccupiedAtRevisionZero() throws Exception {
        Tableau t = tableau();

        assertThat(t.size(), is(4));
        assertThat(t.handSize(), is(4));
        assertThat(t.occupiedSlots(), contains(0, 1, 2, 3));
        assertThat(t.revision(2), is(0));
        assertThat(t.points(), is(1 + 2 + 3 + 13));
    }

    @Test
    void whenReplace_thenOldCardReturnedAndRevisionBumped() throws Exception {
        Tableau t = tableau();

        assertThat(t.replace(1, card("9S")), is(card("2H")));

        assertThat(t.card(1), is(card("9S")));
        assertThat(t.revision(1), is(1));
        assertThat(t.revision(0), is(0));
    }

    @Test
    void whenRemove_thenSlotStaysEmptyAndOthersDoNotShift() throws Exception {
        Tableau t = tableau();

        t.remove(1);

        assertThat(t.size(), is(4));
        assertThat(t.handSize(), is(3));
        assertThat(t.isOccupied(1), is(false));
        assertThat(t.card(2), is(card("3H")));
        assertThat(t.revision(1), is(1));
        assertThrows(EmptySlotException.class, () -> t.card(1));
    }

    @Test
    void whenAppend_thenNewSlotAtTheEndAndEmptySlotNotReused() throws Exception {
        Tableau t = tableau();
        t.remove(0);

        int slot = t.append(card("5C"));

        assertThat(slot, is(4));
        assertThat(t.isOccupied(0), is(false));
        assertThat(t.revision(4), is(0));
    }

    @Test
    void whenPutIntoEmptySlot_thenFilledAndRevisionBumped() throws Exception {
        Tableau t = tableau();
        t.remove(3);

        t.put(3, card("4D"));

        assertThat(t.card(3), is(card("4D")));
        assertThat(t.revision(3), is(2));
    }

    @Test
    void givenUnknownSlot_thenInvalidSlot() {
        Tableau t = tableau();

        assertThrows(InvalidSlotException.class, () -> t.card(9));
        assertThrows(InvalidSlotException.class, () -> t.card(-1));
    }

    @Test
    void slotsViewListsEverySlotIncludingEmptyOnes() throws Exception {
        Tableau t = tableau();
        t.remove(2);

        assertThat(t.slots().get(2).occupied(), is(false));
        assertThat(t.slots().get(0).occupied(), is(true));
        assertThat(t.slots().size(), is(4));
    }
}
