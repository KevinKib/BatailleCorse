package org.kevinkib.bataillecorse.core.domain;

import org.kevinkib.cards.testhelpers.CardFixtures;
import org.kevinkib.cards.testhelpers.HandBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlayerFixtures {

    public static Player anyPlayer() {
        return PlayerBuilder.aPlayer().withEmptyHand().build();
    }

    public static List<Player> createNumberOfPlayers(int nbPlayers) {
        return createNumberOfPlayersWithNoCards(nbPlayers);
    }

    public static List<Player> createNumberOfPlayersWithNoCards(int nbPlayers) {
        List<Player> players = new ArrayList<>();

        for (int i = 0; i < nbPlayers; ++i) {
            players.add(PlayerBuilder.aPlayer()
                    .withId(i)
                    .withHand(
                            HandBuilder.aHand()
                                    .withNoCards()
                                    .build()
                    )
                    .build());
        }

        return players;
    }

    public static List<Player> createNumberOfPlayersWithAnyCards(int nbPlayers) {
        return createNumberOfPlayersWithOneCard(nbPlayers);
    }

    public static List<Player> createNumberOfPlayersWithOneCard(int nbPlayers) {
        List<Player> players = new ArrayList<>();

        for (int i = 0; i < nbPlayers; ++i) {
            players.add(PlayerBuilder.aPlayer()
                    .withId(i)
                    .withHand(
                            HandBuilder.aHand()
                                    .withCards(Collections.singletonList(CardFixtures.anyCard()))
                                    .build()
                    )
                    .build());
        }

        return players;
    }

}
