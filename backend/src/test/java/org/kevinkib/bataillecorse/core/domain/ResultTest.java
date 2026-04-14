package org.kevinkib.bataillecorse.core.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.testhelpers.HandFixtures;

import java.util.Arrays;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.kevinkib.bataillecorse.core.domain.slaprules.SlapRulesFixtures.alwaysApplyingSlapRules;
import static org.kevinkib.bataillecorse.core.domain.slaprules.SlapRulesFixtures.anySlapRules;
import static org.kevinkib.cards.testhelpers.CardFixtures.anyCard;

public class ResultTest {

    @Test
    public void givenNonEmptyPile_thenReturnsNull() {
        Result result = Result.update(
                PlayerFixtures.createNumberOfPlayersWithAnyCards(2),
                CentralPileFixtures.createCentralPileThenAddCards(anyCard()),
                anySlapRules()
        );

        assertNull(result.winningPlayer());
    }

    @Test
    public void givenNonEmptyPile_andMultiplePlayersWithCards_thenReturnsNull() {
        Result result = Result.update(
                Arrays.asList(
                    PlayerBuilder.aPlayer().withId(1).withHand(
                            HandFixtures.createHandWithCards(anyCard())
                    ).build(),
                    PlayerBuilder.aPlayer().withId(2).withHand(
                            HandFixtures.createHandWithCards(anyCard())
                    ).build()
                ),
                CentralPileFixtures.createCentralPileThenAddCards(anyCard()),
                anySlapRules()
        );

        assertNull(result.winningPlayer());
    }

    @Test
    public void givenEmptyPile_andOnlyOnePlayerWithCards_thenReturnsPlayer() {
        Player winningPlayer = PlayerBuilder.aPlayer().withId(1).withHand(
                HandFixtures.createHandWithCards(anyCard())
        ).build();

        Result result = Result.update(
                Arrays.asList(
                        winningPlayer,
                        PlayerBuilder.aPlayer().withId(2).withHand(
                                HandFixtures.createHandWithNoCards()
                        ).build()
                ),
                CentralPileFixtures.createEmptyCentralPile(),
                anySlapRules()
        );

        assertThat(result.winningPlayer(), is(winningPlayer));
    }

    @Test
    public void givenNonEmptyAndSlappablePile_andOnlyOnePlayerWithCards_thenReturnsNull() {
        Player winningPlayer = PlayerBuilder.aPlayer().withId(1).withHand(
                HandFixtures.createHandWithCards(anyCard())
        ).build();

        Result result = Result.update(
                Arrays.asList(
                        winningPlayer,
                        PlayerBuilder.aPlayer().withId(2).withEmptyHand().build()
                ),
                CentralPileFixtures.createCentralPileThenAddCards(anyCard()),
                alwaysApplyingSlapRules()
        );

        assertNull(result.winningPlayer());
    }


}
