package org.kevinkib.bataillecorse.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.french.FrenchRank;
import org.kevinkib.cards.testhelpers.CardBuilder;
import org.kevinkib.cards.testhelpers.PileFixtures;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.kevinkib.bataillecorse.domain.CentralPileState.*;
import static org.kevinkib.cards.testhelpers.CardFixtures.anyCard;

class CentralPileTest {

    @Nested
    class AddTest {

        private final Player player = PlayerBuilder.aPlayer().build();

        @Test
        public void givenPile_andNonHonourCard_whenAdd_thenShouldHaveHonorState() {

            CentralPile centralPile = CentralPileBuilder.aCentralPile()
                    .withPile(PileFixtures.createEmptyPile())
                    .withState(NEUTRAL)
                    .build();

            assertDoesNotThrow(() -> {
                Card card = CardBuilder.aCard().withRank(FrenchRank.NINE).build();
                centralPile.add(card, player);
            });

            assertThat(centralPile.getState(), is(NEUTRAL));
        }

        @Test
        public void givenPile_andHonourCard_whenAdd_thenShouldHaveHonorState() {

            CentralPile centralPile = CentralPileBuilder.aCentralPile()
                    .withPile(PileFixtures.createEmptyPile())
                    .withState(NEUTRAL)
                    .build();

            assertDoesNotThrow(() -> {
                Card card = CardBuilder.aCard().withRank(FrenchRank.QUEEN).build();
                centralPile.add(card, player);
            });

            assertThat(centralPile.getState(), is(HONOUR_STATE));
        }

        @Test
        public void givenPile_andHonourCard_andEnoughNonOtherCards_whenAdd_thenShouldHaveFullState() {

            CentralPile centralPile = CentralPileBuilder.aCentralPile()
                    .withPile(PileFixtures.createEmptyPile())
                    .withState(NEUTRAL)
                    .build();

            assertDoesNotThrow(() -> {
                Card honourCard = CardBuilder.aCard().withRank(FrenchRank.QUEEN).build();
                centralPile.add(honourCard, player);

                Card nonHonourCard = CardBuilder.aCard().withRank(FrenchRank.EIGHT).build();
                centralPile.add(nonHonourCard, player);
                centralPile.add(nonHonourCard, player);
            });

            assertThat(centralPile.getState(), is(FULL));
        }

        @Test
        public void givenPile_andHonourCard_andAnotherHonourCard_whenAdd_thenShouldKeepHonourState() {

            CentralPile centralPile = CentralPileBuilder.aCentralPile()
                    .withPile(PileFixtures.createEmptyPile())
                    .withState(NEUTRAL)
                    .build();

            assertDoesNotThrow(() -> {
                Card honourCard = CardBuilder.aCard().withRank(FrenchRank.QUEEN).build();
                centralPile.add(honourCard, player);

                Card nonHonourCard = CardBuilder.aCard().withRank(FrenchRank.EIGHT).build();
                centralPile.add(nonHonourCard, player);

                centralPile.add(honourCard, player);
            });

            assertThat(centralPile.getState(), is(HONOUR_STATE));
        }

        @Test
        public void givenPile_andNonJackHonourCard_andNonHonourCard_whenAdd_thenShouldKeepHonourState() {

            CentralPile centralPile = CentralPileBuilder.aCentralPile()
                    .withPile(PileFixtures.createEmptyPile())
                    .withState(NEUTRAL)
                    .build();

            assertDoesNotThrow(() -> {
                Card honourCard = CardBuilder.aCard().withRank(FrenchRank.KING).build();
                centralPile.add(honourCard, player);

                Card nonHonourCard = CardBuilder.aCard().withRank(FrenchRank.EIGHT).build();
                centralPile.add(nonHonourCard, player);
            });

            assertThat(centralPile.getState(), is(HONOUR_STATE));
        }

        @Test
        public void givenFullState_whenAdd_thenShouldThrowFullCentralPileException() {

            CentralPile centralPile = CentralPileBuilder.aCentralPile()
                    .withPile(PileFixtures.createEmptyPile())
                    .withState(FULL)
                    .build();

            assertThrows(FullCentralPileException.class, () -> {
                centralPile.add(anyCard(), player);
            });
        }

    }

    @Nested
    class AddBelowForPenalityTest {

        private final Card nonHonourCard = CardBuilder.aCard()
                .withRank(FrenchRank.TWO)
                .build();

        private final Card honourCard = CardBuilder.aCard()
                .withRank(FrenchRank.JACK)
                .build();

        @Test
        public void givenNeutralPileWithCards_whenAHonourCardIsAddedForPenality_ThenPileRemainsNeutral() {

            CentralPile centralPile = CentralPileBuilder.aCentralPile()
                    .withCards(nonHonourCard)
                    .withState(NEUTRAL)
                    .build();

            centralPile.addBelowForPenality(honourCard);

            assertThat(centralPile.getState(), is(NEUTRAL));
        }

    }

    @Nested
    class ClearTest {

        @Test
        public void givenPileWithCards_whenClear_thenShouldBeEmpty() {

            CentralPile centralPile = CentralPileBuilder.aCentralPile()
                    .withPile(PileFixtures.createPileWithCard(anyCard()))
                    .build();

            centralPile.clearAndReturnCards();

            assertThat(centralPile.isEmpty(), is(true));
        }

        @Test
        public void givenPileWithCards_whenClear_thenShouldBeInNeutralState() {

            CentralPile centralPile = CentralPileBuilder.aCentralPile()
                    .withPile(PileFixtures.createPileWithCard(anyCard()))
                    .withState(FULL)
                    .build();

            centralPile.clearAndReturnCards();

            assertThat(centralPile.getState(), is(NEUTRAL));
        }

        @Test
        public void givenPileWithCards_whenClear_thenHonourCardInformationsShouldBeReset() {

            CentralPile centralPile = CentralPileBuilder.aCentralPile()
                    .withPile(PileFixtures.createEmptyPile())
                    .withState(NEUTRAL)
                    .build();

            Card honourCard = CardBuilder.aCard().withRank(FrenchRank.ACE).build();
            Player player = PlayerBuilder.aPlayer().build();

            assertDoesNotThrow(() -> {
                centralPile.add(honourCard, player);
                centralPile.clearAndReturnCards();
            });

            assertNull(centralPile.getLastHonourCard());
            assertNull(centralPile.getPlayerThatAddedLastHonourCard());
            assertThat(centralPile.getNbCardsSinceLastHonourCard(), is(0));
        }

    }

    @Nested
    class IsGrabbableTest {

        @Test
        public void givenEmptyPile_thenIsNotGrabbableByAnyPlayer() {

            CentralPile pile = CentralPileFixtures.createEmptyCentralPile();
            Player player = PlayerBuilder.aPlayer().build();

            assertThat(pile.isGrabbableByPlayer(player), is(false));
        }

        @Test
        public void givenNotFullPile_withHonourCard_thenIsNotGrabbableByPlayer_thatAddedHonourCard() {

            Player player1 = PlayerBuilder.aPlayer().withId(1).withCards(anyCard()).build();
            CentralPile pile = CentralPileBuilder.aCentralPile()
                    .withState(NEUTRAL)
                    .build();
            Card honourCard = CardBuilder.aCard().withRank(FrenchRank.JACK).build();

            assertDoesNotThrow(() -> {
                pile.add(honourCard, player1);
            });

            assertThat(pile.isGrabbableByPlayer(player1), is(false));
        }

        @Test
        public void givenFullPile_withHonourCard_thenIsNotGrabbableByPlayer_thatDidNotAddHonourCard() {

            CentralPile pile = CentralPileFixtures.createEmptyCentralPile();
            Player player = PlayerBuilder.aPlayer().withId(1).build();
            Player otherPlayer = PlayerBuilder.aPlayer().withId(2).build();
            Card jackCard = CardBuilder.aCard().withRank(FrenchRank.JACK).build();
            Card otherCard = CardBuilder.aCard().build();

            assertDoesNotThrow(() -> {
                pile.add(jackCard, player);
                pile.add(otherCard, otherPlayer);
            });

            assertThat(pile.isGrabbableByPlayer(otherPlayer), is(false));
        }

        @Test
        public void givenFullPile_withHonourCard_thenIsGrabbableByPlayer_thatAddedHonourCard() {

            CentralPile pile = CentralPileFixtures.createEmptyCentralPile();
            Player player = PlayerBuilder.aPlayer().withId(1).build();
            Player otherPlayer = PlayerBuilder.aPlayer().withId(2).build();
            Card jackCard = CardBuilder.aCard().withRank(FrenchRank.JACK).build();
            Card otherCard = CardBuilder.aCard().build();

            assertDoesNotThrow(() -> {
                pile.add(jackCard, player);
                pile.add(otherCard, otherPlayer);
            });

            assertThat(pile.isGrabbableByPlayer(player), is(true));
        }

    }

    @Nested
    class IsLastCardHonourCardTest {

        @Test
        public void givenEmptyPile_thenReturnFalse() {
            CentralPile pile = CentralPileFixtures.createEmptyCentralPile();

            assertThat(pile.isLastCardHonourCard(), is(false));
        }

        @Test
        public void givenPileWithNotHonourCardOnTop_thenReturnFalse() {
            CentralPile pile = CentralPileBuilder.aCentralPile()
                        .withCardsWithRanks(FrenchRank.FOUR)
                        .build();

            assertThat(pile.isLastCardHonourCard(), is(false));
        }

        @Test
        public void givenPileWithHonourCardOnTop_thenReturnTrue() {
            CentralPile pile = CentralPileBuilder.aCentralPile()
                        .withCardsWithRanks(FrenchRank.QUEEN)
                        .build();

            assertThat(pile.isLastCardHonourCard(), is(true));
        }

    }
}