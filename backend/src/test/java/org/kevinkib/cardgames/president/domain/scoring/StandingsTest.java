package org.kevinkib.cardgames.president.domain.scoring;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class StandingsTest {

    private static PlayerId p(int seat) {
        return new PlayerId(seat);
    }

    @Test
    void theLeaderHoldsTheMostPoints() {
        Standings standings = new Standings(Map.of(p(0), 3, p(1), 7, p(2), 5));

        assertThat(standings.leaders(), is(List.of(p(1))));
        assertThat(standings.ranked(), is(List.of(p(1), p(2), p(0))));
    }

    @Test
    void aTieGivesSeveralLeadersInSeatOrder() {
        Map<PlayerId, Integer> totals = new LinkedHashMap<>();
        totals.put(p(2), 6);
        totals.put(p(0), 6);
        totals.put(p(1), 1);

        assertThat(new Standings(totals).leaders(), is(List.of(p(0), p(2))));
    }

    @Test
    void negativeTotalsRankBelowZero() {
        Standings standings = new Standings(Map.of(p(0), -2, p(1), 0));

        assertThat(standings.leaders(), is(List.of(p(1))));
        assertThat(standings.points(p(0)), is(-2));
    }

    @Test
    void noPlayerMeansNoLeader() {
        assertThat(new Standings(Map.of()).leaders(), is(List.of()));
    }
}
