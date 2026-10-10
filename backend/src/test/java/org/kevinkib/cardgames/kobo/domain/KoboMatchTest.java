package org.kevinkib.cardgames.kobo.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Rank;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.ref;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

class KoboMatchTest {

    private static final Rank SEVEN = card("7H").getRank();
    private static final Rank KING = card("KS").getRank();

    /** Top of the discard is a 7; seat 0 holds a 7 in slot 0, seat 1 holds a 7 in slot 2. */
    private KoboBuilder table() {
        return aKobo()
                .withTableau("7S", "2H", "3H", "4H")
                .withTableau("AC", "2C", "7D", "9C")
                .withTableau("AD", "2D", "3D", "KD")
                .withDiscard("7H", "5H")
                .withDrawPile("8C", "9H", "10H")
                .inPhase(Phase.DRAW);
    }

    // ---- own card

    @Test
    void ownCardOfSameRank_leavesTheTableauAndBecomesTheTop() throws Exception {
        Kobo kobo = table().build();

        KoboEvent event = kobo.matchDiscard(seat(0), ref(0, 0), null, SEVEN);

        assertThat(event instanceof KoboEvent.MatchSucceeded, is(true));
        assertThat(kobo.discardTop().get(), is(card("7S")));
        assertThat(kobo.handSize(seat(0)), is(3));
        assertThat(kobo.slots(seat(0)).get(0).occupied(), is(false));
        assertThat(kobo.slots(seat(0)).get(0).revision(), is(1));
        assertThat(kobo.phase(), is(Phase.DRAW));
        assertThat(kobo.currentSeat(), is(seat(0)));
    }

    @Test
    void anyoneMayMatchOutOfTurn_inEveryLivePhase() throws Exception {
        for (Phase phase : new Phase[]{Phase.DRAW, Phase.DECISION, Phase.POWER, Phase.KING_DECISION, Phase.TURN_END}) {
            Kobo kobo = table().inPhase(phase).currentSeat(1).withDrawn("2S").build();

            kobo.matchDiscard(seat(0), ref(0, 0), null, SEVEN);

            assertThat(phase.name(), kobo.discardTop().get(), is(card("7S")));
            assertThat(kobo.phase(), is(phase));
        }
    }

    @Test
    void colourIsIrrelevant_redAndBlackKingsAnswerEachOther() throws Exception {
        Kobo kobo = table().withDiscard("KH").build();

        kobo.matchDiscard(seat(2), ref(2, 3), null, KING);

        assertThat(kobo.discardTop().get(), is(card("KD")));
    }

    @Test
    void wrongRank_givesThePenaltyAsANewSlotAndHidesTheTarget() throws Exception {
        Kobo kobo = table().build();

        KoboEvent event = kobo.matchDiscard(seat(0), ref(0, 1), null, SEVEN);

        assertThat(event instanceof KoboEvent.MatchPenalised, is(true));
        assertThat(kobo.slots(seat(0)).size(), is(5));
        assertThat(kobo.slots(seat(0)).get(4).card(), is(card("8C")));
        assertThat(kobo.slots(seat(0)).get(4).revision(), is(0));
        assertThat(kobo.slots(seat(0)).get(1).card(), is(card("2H")));
        assertThat(kobo.drawPileSize(), is(2));
        assertThat(kobo.discardTop().get(), is(card("7H")));
        assertThat(event.toString().contains("2H"), is(false));
    }

    @Test
    void penaltyNeverReusesAnEmptySlot() throws Exception {
        Kobo kobo = table().build();
        kobo.matchDiscard(seat(0), ref(0, 0), null, SEVEN);

        kobo.matchDiscard(seat(0), ref(0, 1), null, SEVEN);

        assertThat(kobo.slots(seat(0)).get(0).occupied(), is(false));
        assertThat(kobo.slots(seat(0)).size(), is(5));
    }

    @Test
    void penaltyWithEmptyDrawPile_reshufflesThePile_andWithNothingLeftGivesNoCard() throws Exception {
        Kobo reshuffle = table().withDrawPile().build();
        reshuffle.matchDiscard(seat(0), ref(0, 1), null, SEVEN);
        assertThat(reshuffle.slots(seat(0)).size(), is(5));
        assertThat(reshuffle.discardTop().get(), is(card("7H")));

        Kobo nothing = table().withDrawPile().withDiscard("7H").build();
        KoboEvent event = nothing.matchDiscard(seat(0), ref(0, 1), null, SEVEN);
        assertThat(((KoboEvent.MatchPenalised) event).cardGiven(), is(false));
        assertThat(nothing.slots(seat(0)).size(), is(4));
    }

    @Test
    void emptyTargetSlot_isRejectedWithoutPenaltyOrVersionBump() throws Exception {
        Kobo kobo = table().build();
        kobo.matchDiscard(seat(0), ref(0, 0), null, SEVEN);
        int version = kobo.version();

        assertThrows(EmptySlotException.class, () -> kobo.matchDiscard(seat(1), ref(0, 0), 0, SEVEN));

        assertThat(kobo.version(), is(version));
        assertThat(kobo.slots(seat(1)).size(), is(4));
    }

    @Test
    void unknownSlotOrSeat_isInvalid() {
        Kobo kobo = table().build();

        assertThrows(InvalidSlotException.class, () -> kobo.matchDiscard(seat(0), ref(0, 9), null, SEVEN));
        assertThrows(InvalidSlotException.class, () -> kobo.matchDiscard(seat(0), ref(8, 0), null, SEVEN));
    }

    @Test
    void emptyDiscardPile_isRejectedWithoutPenalty() {
        Kobo kobo = table().withDiscard().build();

        assertThrows(NothingToMatchException.class, () -> kobo.matchDiscard(seat(0), ref(0, 0), null, SEVEN));
        assertThat(kobo.version(), is(0));
        assertThat(kobo.slots(seat(0)).size(), is(4));
    }

    @Test
    void staleExpectedRank_isRejectedWithoutPenalty() {
        Kobo kobo = table().build();

        assertThrows(StaleDiscardException.class, () -> kobo.matchDiscard(seat(0), ref(0, 0), null, KING));

        assertThat(kobo.version(), is(0));
        assertThat(kobo.slots(seat(0)).size(), is(4));
        assertThat(kobo.handSize(seat(0)), is(4));
    }

    @Test
    void staleExpectedRevision_isRejectedWithoutPenalty() throws Exception {
        Kobo kobo = table().build();
        kobo.matchDiscard(seat(0), ref(1, 2), 3, SEVEN); // a card is given into (1, 2): revision 2
        int version = kobo.version();

        assertThrows(StaleSlotException.class, () -> kobo.matchDiscard(seat(2), ref(1, 2), 0, SEVEN, 0));

        assertThat(kobo.version(), is(version));
        assertThat(kobo.slots(seat(2)).size(), is(4));
    }

    @Test
    void missingExpectedRank_isAnIllegalArgument() {
        Kobo kobo = table().build();

        assertThrows(IllegalArgumentException.class, () -> kobo.matchDiscard(seat(0), ref(0, 0), null, null));
    }

    @Test
    void outsideLiveRoundPhases_isRejected() {
        for (Phase phase : new Phase[]{Phase.MEMORISING, Phase.GIFT, Phase.ROUND_OVER, Phase.FINISHED}) {
            Kobo kobo = table().inPhase(phase).build();

            Class<? extends Exception> expected =
                    phase == Phase.FINISHED ? FinishedGameException.class : WrongPhaseException.class;
            assertThrows(expected, () -> kobo.matchDiscard(seat(0), ref(0, 0), null, SEVEN));
        }
    }

    @Test
    void aPlayerCanFallToZeroCards() throws Exception {
        Kobo kobo = aKobo().withTableau("7S").withTableau("AC", "2C", "7D", "9C")
                .withDiscard("7H").inPhase(Phase.DRAW).build();

        kobo.matchDiscard(seat(0), ref(0, 0), null, SEVEN);

        assertThat(kobo.handSize(seat(0)), is(0));
        assertThat(kobo.handPoints(seat(0)), is(0));
    }

    // ---- another player's card

    @Test
    void otherPlayersCard_goesToThePile_andTheActorGivesOneOfTheirOwn() throws Exception {
        Kobo kobo = table().build();

        KoboEvent event = kobo.matchDiscard(seat(0), ref(1, 2), 3, SEVEN);

        assertThat(kobo.discardTop().get(), is(card("7D")));
        assertThat(kobo.slots(seat(1)).get(2).card(), is(card("4H")));
        assertThat(kobo.slots(seat(1)).get(2).revision(), is(2));
        assertThat(kobo.slots(seat(0)).get(3).occupied(), is(false));
        assertThat(kobo.slots(seat(0)).get(3).revision(), is(1));
        assertThat(kobo.slots(seat(1)).size(), is(4));
        assertThat(((KoboEvent.MatchSucceeded) event).given(), is(ref(0, 3)));
    }

    @Test
    void otherPlayersCard_wrongRankOnlyPenalisesTheActor() throws Exception {
        Kobo kobo = table().build();

        kobo.matchDiscard(seat(0), ref(1, 0), 1, SEVEN);

        assertThat(kobo.slots(seat(0)).size(), is(5));
        assertThat(kobo.slots(seat(0)).get(1).card(), is(card("2H")));
        assertThat(kobo.slots(seat(1)).get(0).card(), is(card("AC")));
    }

    @Test
    void invalidGiveSlot_isRejectedBeforeAnyChange() {
        Kobo kobo = table().build();

        assertThrows(InvalidGiveSlotException.class, () -> kobo.matchDiscard(seat(0), ref(1, 2), null, SEVEN));
        assertThrows(InvalidGiveSlotException.class, () -> kobo.matchDiscard(seat(0), ref(1, 2), 9, SEVEN));

        assertThat(kobo.version(), is(0));
        assertThat(kobo.slots(seat(1)).get(2).card(), is(card("7D")));
    }

    @Test
    void actorWithNoCard_givesNothingAndTheSlotStaysEmpty() throws Exception {
        Kobo kobo = aKobo().withTableau().withTableau("AC", "2C", "7D", "9C")
                .withDiscard("7H").inPhase(Phase.DRAW).build();

        kobo.matchDiscard(seat(0), ref(1, 2), null, SEVEN);

        assertThat(kobo.slots(seat(1)).get(2).occupied(), is(false));
        assertThat(kobo.discardTop().get(), is(card("7D")));
    }

    @Test
    void giveSlotIsIgnoredOnAnOwnCard() throws Exception {
        Kobo kobo = table().build();

        kobo.matchDiscard(seat(0), ref(0, 0), 9, SEVEN);

        assertThat(kobo.handSize(seat(0)), is(3));
    }

    @Test
    void cardsAreConserved() throws Exception {
        Kobo kobo = table().build();
        int before = kobo.cardsInPlay();

        kobo.matchDiscard(seat(0), ref(1, 2), 3, SEVEN);
        kobo.matchDiscard(seat(2), ref(2, 0), null, SEVEN);

        assertThat(kobo.cardsInPlay(), is(before));
    }
}
