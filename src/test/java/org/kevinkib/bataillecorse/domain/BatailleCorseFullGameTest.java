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
import static org.kevinkib.cards.domain.deck.french.FrenchRank.*;
import static org.kevinkib.cards.testhelpers.CardFixtures.CardRanksMatcher.areCardsOfRanks;

public class BatailleCorseFullGameTest {

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

            assertThat(player1.hand().getCards(), areCardsOfRanks(
                    TWO, THREE, THREE, NINE, ACE, FOUR, SEVEN, JACK, EIGHT
            ));

            assertThat(player2.hand().getCards(), areCardsOfRanks(
                    SIX, KING, FIVE, EIGHT, SEVEN
            ));

            batailleCorse.send(player1); // Two
            batailleCorse.send(player2); // Six
            batailleCorse.send(player1); // Three
            batailleCorse.send(player2); // King
            batailleCorse.send(player1); // Three

            batailleCorse.slap(player2);

            assertThat(player1.hand().getCards(), areCardsOfRanks(
                    NINE, ACE, FOUR, SEVEN, JACK, EIGHT
            ));

            assertThat(player2.hand().getCards(), areCardsOfRanks(
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

            assertThat(player1.hand().getCards(), areCardsOfRanks(
                    FOUR, SEVEN, JACK, EIGHT, FIVE, NINE, EIGHT, ACE, SEVEN, TWO, SIX, THREE
            ));

            assertThat(player2.hand().getCards(), areCardsOfRanks(
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

            assertThat(player2.hasAnyCards(), is(false));

            assertThat(batailleCorse.isFinished(), is(true));
            assertThat(batailleCorse.getWinner(), is(player1));

        });
    }

    @Test
    public void givenDefaultRulesAndPenalities_and4Players_thenShouldPlayFullGame() {

        Player player1 = PlayerBuilder.aPlayer().withId(1)
                .withCardsWithRanks(NINE, FOUR, SEVEN, JACK, TWO, THREE, THREE)
                .build();

        Player player2 = PlayerBuilder.aPlayer().withId(2)
                .withCardsWithRanks(ACE, EIGHT, SIX, KING, FIVE, EIGHT, SEVEN)
                .build();

        Player player3 = PlayerBuilder.aPlayer().withId(3)
                .withCardsWithRanks(TEN, JACK, SEVEN, TWO, THREE, QUEEN, KING)
                .build();

        Player player4 = PlayerBuilder.aPlayer().withId(4)
                .withCardsWithRanks(JACK, FIVE, SEVEN, TWO, FIVE, ACE, THREE)
                .build();

        batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                .withPlayers(Arrays.asList(player1, player2, player3, player4))
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
            batailleCorse.send(player3); // Ten
            batailleCorse.slap(player1);

            assertThat(player1.getCards(), areCardsOfRanks(
                    FOUR, SEVEN, JACK, TWO, THREE, THREE, NINE, ACE, TEN
            ));

            assertThat(player2.getCards(), areCardsOfRanks(
                    EIGHT, SIX, KING, FIVE, EIGHT, SEVEN
            ));

            assertThat(player3.getCards(), areCardsOfRanks(
                    JACK, SEVEN, TWO, THREE, QUEEN, KING
            ));

            assertThat(player4.getCards(), areCardsOfRanks(
                    JACK, FIVE, SEVEN, TWO, FIVE, ACE, THREE
            ));

            batailleCorse.send(player1); // Four
            batailleCorse.send(player2); // Eight
            batailleCorse.send(player3); // Jack
            batailleCorse.send(player4); // Jack
            batailleCorse.slap(player1);

            assertThat(player1.getCards(), areCardsOfRanks(
                    SEVEN, JACK, TWO, THREE, THREE, NINE, ACE, TEN, FOUR, EIGHT, JACK, JACK
            ));

            assertThat(player2.getCards(), areCardsOfRanks(
                    SIX, KING, FIVE, EIGHT, SEVEN
            ));

            assertThat(player3.getCards(), areCardsOfRanks(
                    SEVEN, TWO, THREE, QUEEN, KING
            ));

            assertThat(player4.getCards(), areCardsOfRanks(
                    FIVE, SEVEN, TWO, FIVE, ACE, THREE
            ));

            batailleCorse.send(player1); // Seven
            batailleCorse.send(player2); // Six
            batailleCorse.slap(player4); // Wrong slap
            batailleCorse.send(player3); // Seven
            batailleCorse.slap(player1);

            assertThat(player1.getCards(), areCardsOfRanks(
                    JACK, TWO, THREE, THREE, NINE, ACE, TEN, FOUR, EIGHT, JACK, JACK,
                    SEVEN, FIVE, SEVEN, SIX, SEVEN
            ));

            assertThat(player2.getCards(), areCardsOfRanks(
                    KING, FIVE, EIGHT, SEVEN
            ));

            assertThat(player3.getCards(), areCardsOfRanks(
                    TWO, THREE, QUEEN, KING
            ));

            assertThat(player4.getCards(), areCardsOfRanks(
                    TWO, FIVE, ACE, THREE
            ));

            batailleCorse.send(player1); // Jack
            batailleCorse.send(player2); // King
            batailleCorse.send(player3); // Two
            batailleCorse.send(player3); // Three
            batailleCorse.send(player3); // Queen
            batailleCorse.send(player4); // Two
            batailleCorse.send(player4); // Five

            assertThrows(CannotGrabException.class, () -> {
                batailleCorse.grab(player4);
            });

            assertThrows(FullCentralPileException.class, () -> {
                batailleCorse.send(player1);
            });

            batailleCorse.grab(player3);

            assertThat(player1.getCards(), areCardsOfRanks(
                    TWO, THREE, THREE, NINE, ACE, TEN, FOUR, EIGHT, JACK, JACK,
                    SEVEN, FIVE, SEVEN, SIX, SEVEN
            ));

            assertThat(player2.getCards(), areCardsOfRanks(
                    FIVE, EIGHT, SEVEN
            ));

            assertThat(player3.getCards(), areCardsOfRanks(
                    KING, JACK, KING, TWO, THREE, QUEEN, TWO, FIVE
            ));

            assertThat(player4.getCards(), areCardsOfRanks(
                    ACE, THREE
            ));

            batailleCorse.send(player3); // King
            batailleCorse.send(player4); // Ace
            batailleCorse.send(player1); // Two
            batailleCorse.send(player1); // Three
            batailleCorse.send(player1); // Three
            batailleCorse.slap(player1);

            assertThat(player1.getCards(), areCardsOfRanks(
                    NINE, ACE, TEN, FOUR, EIGHT, JACK, JACK,
                    SEVEN, FIVE, SEVEN, SIX, SEVEN, KING, ACE, TWO, THREE, THREE
            ));

            assertThat(player2.getCards(), areCardsOfRanks(
                    FIVE, EIGHT, SEVEN
            ));

            assertThat(player3.getCards(), areCardsOfRanks(
                    JACK, KING, TWO, THREE, QUEEN, TWO, FIVE
            ));

            assertThat(player4.getCards(), areCardsOfRanks(
                    THREE
            ));

            batailleCorse.send(player1); // Nine
            batailleCorse.send(player2); // Five
            batailleCorse.send(player3); // Jack
            batailleCorse.send(player4); // Three

            batailleCorse.grab(player3);

            assertThat(player1.getCards(), areCardsOfRanks(
                    ACE, TEN, FOUR, EIGHT, JACK, JACK,
                    SEVEN, FIVE, SEVEN, SIX, SEVEN, KING, ACE, TWO, THREE, THREE
            ));

            assertThat(player2.getCards(), areCardsOfRanks(
                    EIGHT, SEVEN
            ));

            assertThat(player3.getCards(), areCardsOfRanks(
                    KING, TWO, THREE, QUEEN, TWO, FIVE, NINE, FIVE, JACK, THREE
            ));

            assertThat(player4.hasAnyCards(), is(false));

            batailleCorse.send(player3); // King
            batailleCorse.send(player1); // Ace
            batailleCorse.send(player2); // Eight
            batailleCorse.send(player2); // Seven
            batailleCorse.send(player3); // Two
            batailleCorse.send(player3); // Three
            batailleCorse.grab(player1);

            assertThat(player1.getCards(), areCardsOfRanks(
                    TEN, FOUR, EIGHT, JACK, JACK,
                    SEVEN, FIVE, SEVEN, SIX, SEVEN, KING, ACE, TWO, THREE, THREE,
                    KING, ACE, EIGHT, SEVEN, TWO, THREE
            ));

            assertThat(player2.hasAnyCards(), is(false));

            assertThat(player3.getCards(), areCardsOfRanks(
                    QUEEN, TWO, FIVE, NINE, FIVE, JACK, THREE
            ));

            batailleCorse.send(player1); // Ten
            batailleCorse.send(player3); // Queen
            batailleCorse.slap(player1); // Wrong slap
            batailleCorse.send(player1); // Jack
            batailleCorse.send(player3); // Two
            batailleCorse.grab(player1);

            assertThat(player1.getCards(), areCardsOfRanks(
                    JACK, SEVEN, FIVE, SEVEN, SIX, SEVEN, KING, ACE, TWO, THREE, THREE,
                        KING, ACE, EIGHT, SEVEN, TWO, THREE, EIGHT, FOUR, TEN, QUEEN, JACK, TWO
            ));

            assertThat(player3.getCards(), areCardsOfRanks(
                    FIVE, NINE, FIVE, JACK, THREE
            ));

            batailleCorse.send(player1); // Jack
            batailleCorse.send(player3); // Five
            batailleCorse.grab(player1);

            assertThat(player1.getCards(), areCardsOfRanks(
                    SEVEN, FIVE, SEVEN, SIX, SEVEN, KING, ACE, TWO, THREE, THREE,
                    KING, ACE, EIGHT, SEVEN, TWO, THREE, EIGHT, FOUR, TEN, QUEEN, JACK, TWO,
                    JACK, FIVE
            ));

            assertThat(player3.getCards(), areCardsOfRanks(
                    NINE, FIVE, JACK, THREE
            ));

            batailleCorse.send(player1); // Seven
            batailleCorse.send(player3); // Nine
            batailleCorse.send(player1); // Five
            batailleCorse.send(player3); // Five
            batailleCorse.slap(player1);

            assertThrows(CannotSlapIfNoCardsInPileException.class, () -> {
                batailleCorse.slap(player3);
            });

            assertThat(player1.getCards(), areCardsOfRanks(
                    SEVEN, SIX, SEVEN, KING, ACE, TWO, THREE, THREE,
                    KING, ACE, EIGHT, SEVEN, TWO, THREE, EIGHT, FOUR, TEN, QUEEN, JACK, TWO,
                    JACK, FIVE, SEVEN, NINE, FIVE, FIVE
            ));

            assertThat(player3.getCards(), areCardsOfRanks(
                    JACK, THREE
            ));

            batailleCorse.send(player1); // Seven
            batailleCorse.send(player3); // Jack
            batailleCorse.send(player1); // Six
            batailleCorse.grab(player3);

            assertThat(player1.getCards(), areCardsOfRanks(
                    SEVEN, KING, ACE, TWO, THREE, THREE,
                    KING, ACE, EIGHT, SEVEN, TWO, THREE, EIGHT, FOUR, TEN, QUEEN, JACK, TWO,
                    JACK, FIVE, SEVEN, NINE, FIVE, FIVE
            ));

            assertThat(player3.getCards(), areCardsOfRanks(
                    THREE, SEVEN, JACK, SIX
            ));

            batailleCorse.send(player3); // Three
            batailleCorse.send(player1); // Seven
            batailleCorse.send(player3); // Seven
            batailleCorse.slap(player1);

            assertThat(player1.getCards(), areCardsOfRanks(
                    KING, ACE, TWO, THREE, THREE,
                    KING, ACE, EIGHT, SEVEN, TWO, THREE, EIGHT, FOUR, TEN, QUEEN, JACK, TWO,
                    JACK, FIVE, SEVEN, NINE, FIVE, FIVE, THREE, SEVEN, SEVEN
            ));

            assertThat(player3.getCards(), areCardsOfRanks(
                    JACK, SIX
            ));

            batailleCorse.send(player1); // King
            batailleCorse.send(player3); // Jack
            batailleCorse.send(player1); // Ace
            batailleCorse.send(player3); // Six

            assertThat(player3.hasAnyCards(), is(false));

            assertThat(batailleCorse.isFinished(), is(true));
            assertThat(batailleCorse.getWinner(), is(player1));

        });
    }

}