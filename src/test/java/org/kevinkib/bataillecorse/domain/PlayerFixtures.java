package org.kevinkib.bataillecorse.domain;

import org.kevinkib.cards.testhelpers.HandBuilder;

import java.util.ArrayList;
import java.util.List;

public class PlayerFixtures {

    public static List<Player> createNumberOfPlayers(int nbPlayers) {
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

}
