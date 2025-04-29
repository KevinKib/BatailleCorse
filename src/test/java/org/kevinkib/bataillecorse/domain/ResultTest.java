package org.kevinkib.bataillecorse.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.testhelpers.CardFixtures;
import org.kevinkib.cards.testhelpers.HandFixtures;

import java.util.Arrays;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ResultTest {

    @Test
    public void givenNonEmptyPile_thenReturnsNull() {
        Result result = Result.update(
                PlayerFixtures.createNumberOfPlayers(2),
                CentralPileFixtures.createCentralPileThenAddCards(CardFixtures.anyCard())
        );

        assertNull(result.getWinningPlayer());
    }

    @Test
    public void givenMultiplePlayersWithCards_thenReturnsNull() {
        Result result = Result.update(
                Arrays.asList(
                    PlayerBuilder.aPlayer().withId(1).withHand(
                            HandFixtures.createHandWithCards(CardFixtures.anyCard())
                    ).build(),
                    PlayerBuilder.aPlayer().withId(2).withHand(
                            HandFixtures.createHandWithCards(CardFixtures.anyCard())
                    ).build()
                ),
                CentralPileFixtures.createCentralPileThenAddCards(CardFixtures.anyCard())
        );

        assertNull(result.getWinningPlayer());
    }

    @Test
    public void givenOnlyOnePlayerWithCards_thenReturnsPlayer() {
        Player winningPlayer = PlayerBuilder.aPlayer().withId(1).withHand(
                HandFixtures.createHandWithCards(CardFixtures.anyCard())
        ).build();

        Result result = Result.update(
                Arrays.asList(
                        winningPlayer,
                        PlayerBuilder.aPlayer().withId(2).withHand(
                                HandFixtures.createHandWithNoCards()
                        ).build()
                ),
                CentralPileFixtures.createEmptyCentralPile()
        );

        assertThat(result.getWinningPlayer(), is(winningPlayer));
    }


}
