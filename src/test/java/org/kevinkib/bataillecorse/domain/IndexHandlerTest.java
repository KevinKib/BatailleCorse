package org.kevinkib.bataillecorse.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.french.FrenchRank;
import org.kevinkib.cards.testhelpers.CardBuilder;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class IndexHandlerTest {

    @Nested
    public class UpdateTest {

        @Test
        public void givenDefaultHandler_thenIncreaseIndexByOne() {

            int defaultIndex = 0;

            IndexHandler handler = IndexHandlerBuilder.anIndexHandler()
                    .withDefaultIndex(defaultIndex)
                    .withPile(CentralPileFixtures.createEmptyCentralPile())
                    .build();

            Integer index = handler.update();

            Integer expectedIndex = defaultIndex + 1;
            assertThat(index, is(expectedIndex));
        }

        @Test
        public void givenMaximumIndex_thenSetToZero() {

            int nbPlayers = 4;
            int defaultIndex = nbPlayers - 1;

            IndexHandler handler = IndexHandlerBuilder.anIndexHandler()
                    .withDefaultIndex(defaultIndex)
                    .withNbPlayers(nbPlayers)
                    .withPile(CentralPileFixtures.createEmptyCentralPile())
                    .build();

            Integer index = handler.update();

            assertThat(index, is(0));
        }

        @Test
        public void givenHonourPile_andLastCardIsHonour_thenIncreaseIndexByOne() {

            int defaultIndex = 2;

            IndexHandler handler = IndexHandlerBuilder.anIndexHandler()
                    .withDefaultIndex(defaultIndex)
                    .withPile(CentralPileFixtures.createCentralPileThenAddCards(
                            CardBuilder.aCard().withRank(FrenchRank.JACK).build())
                    )
                    .build();

            Integer index = handler.update();

            Integer expectedIndex = defaultIndex + 1;
            assertThat(index, is(expectedIndex));
        }

        @Test
        public void givenHonourPile_andLastCardIsNotHonour_thenIndexDoesNotChange() {

            int defaultIndex = 2;

            IndexHandler handler = IndexHandlerBuilder.anIndexHandler()
                    .withDefaultIndex(defaultIndex)
                    .withPile(CentralPileFixtures.createCentralPileThenAddCards(
                            CardBuilder.aCard().withRank(FrenchRank.FOUR).build(),
                            CardBuilder.aCard().withRank(FrenchRank.ACE).build())
                    )
                    .build();

            Integer index = handler.update();

            Integer expectedIndex = defaultIndex;
            assertThat(index, is(expectedIndex));
        }

    }


}
