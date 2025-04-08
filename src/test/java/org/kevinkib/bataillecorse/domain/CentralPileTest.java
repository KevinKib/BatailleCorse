package org.kevinkib.bataillecorse.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.french.FrenchRank;
import org.kevinkib.cards.testhelpers.CardBuilder;
import org.kevinkib.cards.testhelpers.PileFixtures;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.bataillecorse.domain.CentralPileState.*;
import static org.kevinkib.cards.testhelpers.CardFixtures.anyCard;

class CentralPileTest {

    @Nested
    class AddTest {

        @Test
        public void givenPile_andNonHonourCard_whenAdd_thenShouldHaveHonorState() {

            CentralPile centralPile = CentralPileBuilder.aCentralPile()
                    .withPile(PileFixtures.createEmptyPile())
                    .withState(NEUTRAL)
                    .build();

            assertDoesNotThrow(() -> {
                Card card = CardBuilder.aCard().withRank(FrenchRank.NINE).build();
                centralPile.add(card);
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
                centralPile.add(card);
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
                centralPile.add(honourCard);

                Card nonHonourCard = CardBuilder.aCard().withRank(FrenchRank.EIGHT).build();
                centralPile.add(nonHonourCard);
                centralPile.add(nonHonourCard);
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
                centralPile.add(honourCard);

                Card nonHonourCard = CardBuilder.aCard().withRank(FrenchRank.EIGHT).build();
                centralPile.add(nonHonourCard);

                centralPile.add(honourCard);
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
                centralPile.add(anyCard());
            });
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

    }

}