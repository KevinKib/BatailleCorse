package org.kevinkib.bataillecorse.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.domain.penality.PutCardsUnderPileBuilder;
import org.kevinkib.bataillecorse.domain.slaprules.*;
import org.kevinkib.cards.domain.french.FrenchRank;
import org.kevinkib.cards.testhelpers.CardBuilder;
import org.kevinkib.cards.testhelpers.HandBuilder;

import java.util.Arrays;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class BatailleCorseIT {

    private BatailleCorse batailleCorse;

    @Test
    public void givenDefaultRulesAndPenalities_and2Players_thenShouldPlayFullGame() {

        Player player1 = PlayerBuilder.aPlayer().withId(1)
                .withHand(HandBuilder.aHand().withCards(
                        Arrays.asList(
                                CardBuilder.aCard().withRank(FrenchRank.NINE).build(),
                                CardBuilder.aCard().withRank(FrenchRank.FOUR).build(),
                                CardBuilder.aCard().withRank(FrenchRank.SEVEN).build(),
                                CardBuilder.aCard().withRank(FrenchRank.JACK).build(),
                                CardBuilder.aCard().withRank(FrenchRank.TWO).build()
                        ))
                        .build())
                .build();
        Player player2 = PlayerBuilder.aPlayer().withId(2)
                .withHand(HandBuilder.aHand().withCards(
                                Arrays.asList(
                                        CardBuilder.aCard().withRank(FrenchRank.ACE).build(),
                                        CardBuilder.aCard().withRank(FrenchRank.EIGHT).build(),
                                        CardBuilder.aCard().withRank(FrenchRank.SIX).build(),
                                        CardBuilder.aCard().withRank(FrenchRank.KING).build(),
                                        CardBuilder.aCard().withRank(FrenchRank.FIVE).build()
                                ))
                        .build())
                .build();

        batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                .withPlayers(Arrays.asList(player1, player2))
                .withCurrentPlayer(0)
                .withCentralPile(CentralPileBuilder.aCentralPile()
                        .withNoCards()
                        .withState(CentralPileState.NEUTRAL)
                        .build()
                )
                .withSlapRules(SlapRulesBuilder.aSlapRules()
                        .withRules(Arrays.asList(
                                new CanSlapTens(),
                                new CanSlapSameCardAsBelow(),
                                new CanSlapSandwich(),
                                new CanSlapSumOfTen()
                        ))
                        .build())
                .withPenality(PutCardsUnderPileBuilder.aPutCardsUnderPilePenality()
                        .withGivenCards(2)
                        .build())
                .build();

        assertDoesNotThrow(() -> {
            batailleCorse.send(player1); // Nine
            batailleCorse.send(player2); // Ace
            batailleCorse.send(player1); // Four
            batailleCorse.send(player1); // Seven
            batailleCorse.send(player1); // Jack
            batailleCorse.send(player2); // Eight

            batailleCorse.grab(player1);

            assertThat(player1.getHand().getCards(), is(Arrays.asList(

            )));
        });
    }
}
