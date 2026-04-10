package org.kevinkib.bataillecorse.core.domain;

import org.kevinkib.bataillecorse.core.domain.slaprules.SlapRules;
import org.kevinkib.bataillecorse.core.domain.penality.Penality;
import org.kevinkib.cards.testhelpers.HandBuilder;

import java.util.ArrayList;
import java.util.List;

public final class BatailleCorseBuilder {
    private BatailleCorseId id;
    private List<Player> players;
    private int currentPlayer;
    private CentralPile pile;
    private SlapRules slapRules;
    private Penality penality;

    private BatailleCorseBuilder() {
    }

    public static BatailleCorseBuilder aBatailleCorse() {
        return new BatailleCorseBuilder();
    }

    public BatailleCorseBuilder withId(BatailleCorseId id) {
        this.id = id;
        return this;
    }

    public BatailleCorseBuilder withPlayers(List<Player> players) {
        this.players = players;
        return this;
    }

    public BatailleCorseBuilder withNbPlayers(int nbPlayers) {
        players = new ArrayList<>();
        for (int i = 0; i < nbPlayers; ++i) {
            Player player = PlayerBuilder.aPlayer()
                    .withId(i)
                    .withHand(HandBuilder.aHand()
                            .withNoCards()
                            .build())
                    .build();

            players.add(player);
        }
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

    public BatailleCorseBuilder withEmptyCentralPile() {
        this.pile = CentralPileFixtures.createEmptyCentralPile();
        return this;
    }

    public BatailleCorseBuilder withSlapRules(SlapRules slapRules) {
        this.slapRules = slapRules;
        return this;
    }

    public BatailleCorseBuilder withPenality(Penality penality) {
        this.penality = penality;
        return this;
    }

    public BatailleCorse build() {
        return new BatailleCorse(id, players, currentPlayer, pile, slapRules, penality);
    }

    public BatailleCorse buildAndInitialize() {
        return new BatailleCorse(id, players.size());
    }
}
