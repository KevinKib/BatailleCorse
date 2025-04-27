package org.kevinkib.bataillecorse.domain;

public final class IndexHandlerBuilder {

    private Integer defaultIndex;
    private Integer nbPlayers;
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
        this.nbPlayers = nbPlayers;
        return this;
    }

    public IndexHandlerBuilder withPile(CentralPile pile) {
        this.pile = pile;
        return this;
    }

    public IndexHandler build() {
        return new IndexHandler(defaultIndex, nbPlayers, pile);
    }
}
