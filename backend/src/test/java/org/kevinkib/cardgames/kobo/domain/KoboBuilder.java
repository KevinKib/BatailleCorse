package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.kevinkib.cardgames.kobo.domain.power.Power;
import org.kevinkib.cardgames.kobo.domain.tableau.SlotRef;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.cards;

/** Builds a Kobo in a given phase from explicit tableaux and piles (piles listed top first). */
public final class KoboBuilder {

    private List<List<Card>> tableaux = new ArrayList<>();
    private List<Card> drawPile = cards("2C", "3C", "4C", "5C", "6C");
    private List<Card> discard = new ArrayList<>();
    private Phase phase = Phase.DRAW;
    private int current = 0;
    private Card drawn;
    private Power power;
    private int[] totals;

    public static KoboBuilder aKobo() {
        return new KoboBuilder();
    }

    /** One four-card tableau per call, e.g. {@code withTableau("AH", "2H", "3H", "4H")}. */
    public KoboBuilder withTableau(String... codes) {
        tableaux.add(cards(codes));
        return this;
    }

    public KoboBuilder withPlayers(int n) {
        for (int i = 0; i < n; i++) {
            tableaux.add(cards("AH", "2H", "3H", "4H"));
        }
        return this;
    }

    public KoboBuilder withDrawPile(String... codes) {
        this.drawPile = new ArrayList<>(Arrays.asList(cards(codes).toArray(new Card[0])));
        return this;
    }

    public KoboBuilder withDiscard(String... codes) {
        this.discard = new ArrayList<>(cards(codes));
        return this;
    }

    public KoboBuilder inPhase(Phase phase) {
        this.phase = phase;
        return this;
    }

    public KoboBuilder currentSeat(int seat) {
        this.current = seat;
        return this;
    }

    public KoboBuilder withDrawn(String code) {
        this.drawn = KoboFixtures.card(code);
        return this;
    }

    public KoboBuilder withPendingPower(Power power) {
        this.power = power;
        return this;
    }

    public KoboBuilder withTotals(int... totals) {
        this.totals = totals;
        return this;
    }

    public Kobo build() {
        Kobo kobo = new Kobo(GameId.generate(), 42L, tableaux, drawPile, discard, phase, new PlayerId(current));
        if (drawn != null) {
            kobo.setDrawn(drawn);
        }
        if (totals != null) {
            for (int i = 0; i < totals.length; i++) {
                kobo.setTotal(new PlayerId(i), totals[i]);
            }
        }
        if (power != null) {
            kobo.setPendingPower(power);
        }
        return kobo;
    }

    public static PlayerId seat(int i) {
        return new PlayerId(i);
    }

    public static SlotRef ref(int seat, int slot) {
        return new SlotRef(new PlayerId(seat), slot);
    }
}
