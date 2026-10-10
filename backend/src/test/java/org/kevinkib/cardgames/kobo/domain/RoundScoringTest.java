package org.kevinkib.cardgames.kobo.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoundScoringTest {

    private static final PlayerId P0 = new PlayerId(0);
    private static final PlayerId P1 = new PlayerId(1);
    private static final PlayerId P2 = new PlayerId(2);

    private static Map<PlayerId, Integer> of(int a, int b, int c) {
        Map<PlayerId, Integer> m = new LinkedHashMap<>();
        m.put(P0, a);
        m.put(P1, b);
        m.put(P2, c);
        return m;
    }

    @Test
    void givenNoAnnouncer_thenTotalsAddHandValues() {
        RoundResult r = RoundScoring.score(of(5, 7, 9), null, null, of(10, 0, 20));

        assertThat(r.totalsAfter(), is(of(15, 7, 29)));
        assertThat(r.announcerWon(), is(false));
        assertThat(r.giftTo(), nullValue());
    }

    @Test
    void givenAnnouncerWithFewestPoints_thenScoresZeroAndGivesTen() {
        RoundResult r = RoundScoring.score(of(3, 7, 9), P0, P2, of(0, 0, 0));

        assertThat(r.announcerWon(), is(true));
        assertThat(r.roundPoints(), is(of(0, 7, 9)));
        assertThat(r.totalsAfter(), is(of(0, 7, 19)));
        assertThat(r.giftTo(), is(P2));
    }

    @Test
    void givenAnnouncerTiedWithLowest_thenStillWins() {
        RoundResult r = RoundScoring.score(of(7, 7, 9), P0, P1, of(0, 0, 0));

        assertThat(r.announcerWon(), is(true));
        assertThat(r.totalsAfter(), is(of(0, 17, 9)));
    }

    @Test
    void givenAnnouncerNotLowest_thenHandPlusTwentyAndNoGift() {
        RoundResult r = RoundScoring.score(of(8, 7, 9), P0, null, of(1, 2, 3));

        assertThat(r.announcerWon(), is(false));
        assertThat(r.roundPoints(), is(of(28, 7, 9)));
        assertThat(r.totalsAfter(), is(of(29, 9, 12)));
    }

    @Test
    void givenTotalExactly75Or100_thenDropsTo50() {
        RoundResult r = RoundScoring.score(of(5, 5, 5), null, null, of(70, 95, 74));

        assertThat(r.totalsAfter(), is(of(50, 50, 79)));
        assertThat(r.reset(), org.hamcrest.Matchers.containsInAnyOrder(P0, P1));
    }

    @Test
    void givenNeighbouringTotals_thenUnchanged() {
        RoundResult r = RoundScoring.score(of(1, 1, 2), null, null, of(75, 98, 99));

        assertThat(r.totalsAfter(), is(of(76, 99, 101)));
    }

    @Test
    void givenGiftReachesExactly100_thenResetAfterTheGift() {
        RoundResult r = RoundScoring.score(of(1, 5, 5), P0, P1, of(0, 85, 0));

        assertThat(r.totalsAfter(), is(of(0, 50, 5)));
        assertThat(r.reset(), contains(P1));
    }

    @Test
    void givenResetApplies_thenItIsNotLimitedToAnnouncerOrGifted() {
        RoundResult r = RoundScoring.score(of(1, 5, 5), P0, P1, of(0, 0, 70));

        assertThat(r.totalsAfter(), is(of(0, 15, 50)));
    }

    @Test
    void givenInvalidGift_thenRejected() {
        assertThrows(IllegalArgumentException.class, () -> RoundScoring.score(of(1, 5, 5), P0, P0, of(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> RoundScoring.score(of(1, 5, 5), P0, null, of(0, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> RoundScoring.score(of(1, 5, 5), P0, new PlayerId(9), of(0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> RoundScoring.score(of(9, 5, 5), P0, P1, of(0, 0, 0)));
    }

    @Test
    void givenNoResetNeeded_thenResetSetIsEmpty() {
        assertThat(RoundScoring.score(of(1, 1, 1), null, null, of(0, 0, 0)).reset(), empty());
    }
}
