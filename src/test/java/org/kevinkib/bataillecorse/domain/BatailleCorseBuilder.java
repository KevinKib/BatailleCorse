package org.kevinkib.bataillecorse.domain;

import java.util.List;

public final class BatailleCorseBuilder {
    private int nbPlayers;
    private List<Player> players;

    private BatailleCorseBuilder() {
    }

    public static BatailleCorseBuilder aBatailleCorse() {
        return new BatailleCorseBuilder();
    }

    public BatailleCorseBuilder withNbPlayers(int nbPlayers) {
        this.nbPlayers = nbPlayers;
        return this;
    }

    public BatailleCorseBuilder withPlayers(List<Player> players) {
        this.players = players;
        return this;
    }

    public BatailleCorse build() {
        if (players != null) {
            return new BatailleCorse(players);
        }

        return new BatailleCorse(nbPlayers);
    }
}