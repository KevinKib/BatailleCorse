package org.kevinkib.cardgames.bataillecorse.domain;

import java.util.List;

public final class IndexHandlerBuilder {

    private Integer defaultIndex;
    private List<Player> players;
    private CentralPile pile;

    private IndexHandlerBuilder() {
    }

    public static IndexHandlerBuilder anIndexHandler() {
        return new IndexHandlerBuilder();
    }

    public IndexHandlerBuilder withDefaultIndex(Integer defaultIndex) {
        this.defaultIndex = defaultIndex;
        return this;
    }

    public IndexHandlerBuilder withNbPlayers(Integer nbPlayers) {
        this.players = PlayerFixtures.createNumberOfPlayers(nbPlayers);
        return this;
    }

    public IndexHandlerBuilder withPlayers(List<Player> players) {
        this.players = players;
        return this;
    }

    public IndexHandlerBuilder withPile(CentralPile pile) {
        this.pile = pile;
        return this;
    }

    public IndexHandler build() {
        return new IndexHandler(defaultIndex, players, pile);
    }
}
