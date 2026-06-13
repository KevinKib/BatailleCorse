package org.kevinkib.cardgames.bataillecorse.domain;

import java.util.stream.Stream;

public class BatailleCorseFixtures {

    public static BatailleCorse createFinishedGame() {

        return BatailleCorseBuilder.aBatailleCorse()
                .withEmptyCentralPile()
                .withPlayers(
                        Stream.concat(
                            PlayerFixtures.createNumberOfPlayersWithAnyCards(1).stream(),
                            PlayerFixtures.createNumberOfPlayers(1).stream()
                        ).toList()
                )
                .build();

    }
}
