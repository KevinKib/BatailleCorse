package org.kevinkib.bataillecorse.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.french.FrenchRank;
import org.kevinkib.cards.testhelpers.CardBuilder;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class IndexHandlerTest {

    @Nested
    public class UpdateTest {

        int nbPlayers = 5;

        @Test
        public void givenDefaultHandler_thenIncreaseIndexToNextPlayerWithCards() {

            int defaultIndex = 0;
            int lastPlayer = nbPlayers - 1;

            IndexHandler handler = IndexHandlerBuilder.anIndexHandler()
                    .withDefaultIndex(defaultIndex)
                    .withPile(CentralPileFixtures.createEmptyCentralPile())
                    .withPlayers(
                            Stream.concat(
                                    PlayerFixtures.createNumberOfPlayersWithNoCards(nbPlayers-1).stream(),
                                    PlayerFixtures.createNumberOfPlayersWithAnyCards(1).stream()
                            ).toList()
                    )
                    .build();

            Integer index = handler.update();

            assertThat(index, is(lastPlayer));
        }

        @Test
        public void givenMaximumIndex_thenSetToZero() {

            int nbPlayers = 4;
            int defaultIndex = nbPlayers - 1;

            IndexHandler handler = IndexHandlerBuilder.anIndexHandler()
                    .withDefaultIndex(defaultIndex)
                    .withPlayers(
                            PlayerFixtures.createNumberOfPlayersWithAnyCards(nbPlayers)
                    )
                    .withPile(CentralPileFixtures.createEmptyCentralPile())
                    .build();

            Integer index = handler.update();

            assertThat(index, is(0));
        }

        @Test
        public void givenHonourPile_andLastCardIsNotHonour_andPlayerCanPlay_thenIndexDoesNotChange() {

            int defaultIndex = 2;

            IndexHandler handler = IndexHandlerBuilder.anIndexHandler()
                    .withDefaultIndex(defaultIndex)
                    .withPile(CentralPileFixtures.createCentralPileThenAddCards(
                            CardBuilder.aCard().withRank(FrenchRank.FOUR).build(),
                            CardBuilder.aCard().withRank(FrenchRank.ACE).build())
                    )
                    .withPlayers(
                            PlayerFixtures.createNumberOfPlayersWithAnyCards(4)
                    )
                    .build();

            Integer index = handler.update();

            Integer expectedIndex = defaultIndex;
            assertThat(index, is(expectedIndex));
        }

        @Test
        public void givenHonourPile_andLastCardIsNotHonour_andPlayerCannotPlay_thenIndexUpdatesToNextAvailablePlayer() {

            int defaultIndex = 0;
            int nbPlayers = 4;
            int lastPlayer = nbPlayers - 1;

            IndexHandler handler = IndexHandlerBuilder.anIndexHandler()
                    .withDefaultIndex(defaultIndex)
                    .withPile(CentralPileFixtures.createCentralPileThenAddCards(
                            CardBuilder.aCard().withRank(FrenchRank.FOUR).build(),
                            CardBuilder.aCard().withRank(FrenchRank.ACE).build())
                    )
                    .withPlayers(
                            Stream.concat(
                                PlayerFixtures.createNumberOfPlayersWithNoCards(nbPlayers-1).stream(),
                                PlayerFixtures.createNumberOfPlayersWithAnyCards(1).stream()
                            ).toList()
                    )
                    .build();

            Integer index = handler.update();

            assertThat(index, is(lastPlayer));
        }

    }


}
