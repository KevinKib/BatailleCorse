package org.kevinkib.bataillecorse.domain;

import org.kevinkib.bataillecorse.domain.hitrules.HitRules;
import org.kevinkib.bataillecorse.domain.penality.Penality;

import java.util.List;

public final class BatailleCorseBuilder {
    private List<Player> players;
    private int currentPlayer;
    private CentralPile pile;
    private HitRules hitRules;
    private Penality penality;

    private Integer nbPlayers;

    private BatailleCorseBuilder() {
    }

    public static BatailleCorseBuilder aBatailleCorse() {
        return new BatailleCorseBuilder();
    }

    public BatailleCorseBuilder withPlayers(List<Player> players) {
        this.players = players;
        return this;
    }

    public BatailleCorseBuilder withNbPlayers(int nbPlayers) {
        this.nbPlayers = nbPlayers;
        return this;
    }

    public BatailleCorseBuilder withCurrentPlayer(int currentPlayer) {
        this.currentPlayer = currentPlayer;
        return this;
    }

    public BatailleCorseBuilder withCentralPile(CentralPile pile) {
        this.pile = pile;
        return this;
    }

    public BatailleCorseBuilder withHitRules(HitRules hitRules) {
        this.hitRules = hitRules;
        return this;
    }

    public BatailleCorseBuilder withPenality(Penality penality) {
        this.penality = penality;
        return this;
    }

    public BatailleCorse build() {
        if (nbPlayers != null) {
            return new BatailleCorse(nbPlayers);
        }
        return new BatailleCorse(players, currentPlayer, pile, hitRules, penality);
    }
}
