package org.kevinkib.bataillecorse.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.domain.penality.PutCardsUnderPileBuilder;
import org.kevinkib.bataillecorse.domain.slaprules.*;
import org.kevinkib.cards.testhelpers.HandBuilder;

import java.util.Arrays;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cards.testhelpers.CardBuilder.aCard;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.kevinkib.cards.domain.french.FrenchRank.*;
import static org.kevinkib.cards.testhelpers.CardFixtures.CardRanksMatcher.areCardsOfRanks;

public class BatailleCorseIT {

    private BatailleCorse batailleCorse;

    @Test
    public void givenDefaultRulesAndPenalities_and2Players_thenShouldPlayFullGame() {

        Player player1 = PlayerBuilder.aPlayer().withId(1)
                .withHand(HandBuilder.aHand().withCards(
                        Arrays.asList(
                                aCard().withRank(NINE).build(),
                                aCard().withRank(FOUR).build(),
                                aCard().withRank(SEVEN).build(),
                                aCard().withRank(JACK).build(),
                                aCard().withRank(TWO).build(),
                                aCard().withRank(THREE).build(),
                                aCard().withRank(THREE).build()
                        ))
                        .build())
                .build();
        Player player2 = PlayerBuilder.aPlayer().withId(2)
                .withHand(HandBuilder.aHand().withCards(
                                Arrays.asList(
                                        aCard().withRank(ACE).build(),
                                        aCard().withRank(EIGHT).build(),
                                        aCard().withRank(SIX).build(),
                                        aCard().withRank(KING).build(),
                                        aCard().withRank(FIVE).build(),
                                        aCard().withRank(EIGHT).build(),
                                        aCard().withRank(SEVEN).build()
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

            assertThat(player1.getHand().getCards(), areCardsOfRanks(
                    TWO, THREE, THREE, NINE, ACE, FOUR, SEVEN, JACK, EIGHT
            ));

            assertThat(player2.getHand().getCards(), areCardsOfRanks(
                    SIX, KING, FIVE, EIGHT, SEVEN
            ));

            batailleCorse.send(player1); // Two
            batailleCorse.send(player2); // Six
            batailleCorse.send(player1); // Three
            batailleCorse.send(player2); // King
            batailleCorse.send(player1); // Three

            batailleCorse.slap(player2);

            assertThat(player1.getHand().getCards(), areCardsOfRanks(
                    NINE, ACE, FOUR, SEVEN, JACK, EIGHT
            ));

            assertThat(player2.getHand().getCards(), areCardsOfRanks(
                    FIVE, EIGHT, SEVEN, TWO, SIX, THREE, KING, THREE
            ));

            batailleCorse.send(player2); // Five
            batailleCorse.send(player1); // Nine
            batailleCorse.send(player2); // Eight
            batailleCorse.send(player1); // Ace
            batailleCorse.send(player2); // Seven
            batailleCorse.send(player2); // Two
            batailleCorse.send(player2); // Six
            batailleCorse.send(player2); // Three

            assertThrows(CannotGrabException.class, () -> {
                batailleCorse.grab(player2);
            });

            batailleCorse.grab(player1);

            assertThat(player1.getHand().getCards(), areCardsOfRanks(
                    FOUR, SEVEN, JACK, EIGHT, FIVE, NINE, EIGHT, ACE, SEVEN, TWO, SIX, THREE
            ));

            assertThat(player2.getHand().getCards(), areCardsOfRanks(
                    KING, THREE
            ));

            batailleCorse.send(player1); // Four
            batailleCorse.send(player2); // King
            batailleCorse.send(player1); // Seven

            assertThrows(NotPlayersTurnException.class, () -> {
                batailleCorse.send(player2);
            });

            batailleCorse.send(player1); // Jack
            batailleCorse.send(player2); // Three

            batailleCorse.grab(player1);

            assertThat(player1.getHand().getCards(), areCardsOfRanks(
                    EIGHT, FIVE, NINE, EIGHT, ACE, SEVEN, TWO, SIX, THREE, FOUR, KING, SEVEN, JACK, THREE
            ));

            assertThat(player2.getHandSize(), is(0));

            assertThat(batailleCorse.getWinner(), is(player1));

        });
    }
}