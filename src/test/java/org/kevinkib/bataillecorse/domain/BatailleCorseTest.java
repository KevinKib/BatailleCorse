package org.kevinkib.bataillecorse.domain;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.domain.hitrules.HitRulesFixtures;
import org.kevinkib.bataillecorse.domain.penality.Penality;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.CardPileState;
import org.kevinkib.cards.domain.Hand;
import org.kevinkib.cards.domain.Pile;

import java.util.Arrays;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.kevinkib.bataillecorse.domain.BatailleCorseTest.InitializationTest.IsEveryCardHidden.everyCardHidden;
import static org.kevinkib.bataillecorse.domain.PlayerFixtures.createNumberOfPlayers;
import static org.kevinkib.cards.testhelpers.PileFixtures.createPileWithNumberOfCards;
import static org.mockito.Mockito.*;

class BatailleCorseTest {

    private BatailleCorse batailleCorse;
    private int nbPlayers;

    @Nested
    class InitializationTest {

        @BeforeEach
        public void beforeEach() {
            nbPlayers = 2;
            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withNbPlayers(nbPlayers)
                    .build();
        }

        @Test
        public void whenInitializing_thenGiveHandsToPlayers_withHiddenCards() {
            batailleCorse = new BatailleCorse(nbPlayers);

            assertNotNull(batailleCorse.getPlayerByIndex(0).getHand());
            assertNotNull(batailleCorse.getPlayerByIndex(1).getHand());

            assertThat(batailleCorse.getPlayerByIndex(0).getHand(), is(everyCardHidden()));
        }

        @Test
        public void whenInitializing_thenDefaultPlayerShouldBePlayerZero() {
            BatailleCorse batailleCorse = new BatailleCorse(nbPlayers);

            assertThat(batailleCorse.getCurrentPlayerIndex(), is(0));
        }

        @Test
        public void whenInitializing_thenPileShouldBeEmpty() {
            BatailleCorse batailleCorse = new BatailleCorse(nbPlayers);

            assertThat(batailleCorse.getPileSize(), is(0));
        }

        static class IsEveryCardHidden extends TypeSafeMatcher<Hand> {

            @Override
            protected boolean matchesSafely(Hand hand) {
                for (Card card : hand.getCards()) {
                    if (!card.isHidden()) {
                        return false;
                    }
                }

                return true;
            }

            public void describeTo(Description description) {
                description.appendText("does not have every card hidden");
            }

            public static Matcher everyCardHidden() {
                return new IsEveryCardHidden();
            }
        }
    }

    @Nested
    class SendTest {

        private BatailleCorse batailleCorse;
        private int nbPlayers;

        @BeforeEach
        public void beforeEach() {
            nbPlayers = 2;
            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withNbPlayers(nbPlayers)
                    .build();
        }

        @Test
        public void givenNotCurrentPlayer_thenReturnNotPlayersTurnException() {
            assertThat(batailleCorse.getCurrentPlayerIndex(), is(0));

            Player wrongPlayer = batailleCorse.getPlayerByIndex(1);
            assertThrows(NotPlayersTurnException.class, () -> {
                batailleCorse.send(wrongPlayer);
            });
        }

        @Test
        public void givenCurrentPlayer_withNoCards_thenReturnNotPlayersTurnException() {
            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                            .withPlayers(Arrays.asList(
                                    PlayerBuilder.aPlayer()
                                            .withEmptyHand()
                                            .build(),
                                    PlayerBuilder.aPlayer()
                                            .build()
                            ))
                            .build();

            Player playerWithEmptyHand = batailleCorse.getCurrentPlayer();
            assertThrows(PlayerCannotPlayException.class, () -> {
                batailleCorse.send(playerWithEmptyHand);
            });
        }

        @Test
        public void givenCurrentPlayer_thenPlayCardOnPile() {

            Player player = batailleCorse.getCurrentPlayer();
            int playerHandSize = player.getHandSize();

            Card playedCard = player.getCardOnTop();

            assertDoesNotThrow(() -> {
                batailleCorse.send(player);
            });

            assertThat(batailleCorse.getPileSize(), is(1));
            assertThat(player.getHandSize(), is(playerHandSize - 1));

            assertThat(batailleCorse.getPileTopCard(), is(playedCard));
            assertThat(batailleCorse.getPileTopCard().getState(), is(CardPileState.SHOWN));
        }

        @Test
        public void givenCurrentPlayer_thenIncreasePlayerIndex() {

            Player player = batailleCorse.getCurrentPlayer();

            assertDoesNotThrow(() -> {
                batailleCorse.send(player);
            });

            assertThat(batailleCorse.getCurrentPlayerIndex(), is(1));
        }

    }

    @Nested
    class HitTest {

        private BatailleCorse batailleCorse;

        @BeforeEach
        public void beforeEach() {
            nbPlayers = 2;
            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withPlayers(Arrays.asList(
                            PlayerBuilder.aPlayer().withId(1).build(),
                            PlayerBuilder.aPlayer().withId(2).build()
                    ))
                    .build();
        }

        @Test
        public void whenLosing_thenApplyPenality() {

            Penality penality = mock(Penality.class);
            Pile pile = mock(Pile.class);

            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withPlayers(createNumberOfPlayers(2))
                    .withHitRules(HitRulesFixtures.neverApplyingRules())
                    .withPenality(penality)
                    .withPile(pile)
                    .build();

            Player player = batailleCorse.getCurrentPlayer();

            batailleCorse.hit(player);

            verify(penality, times(1)).apply(player, pile);
        }

        @Test
        public void whenWinning_thenClearPile_andGivePileCardsToWinningPlayer() {

            int nbCards = 5;
            Pile pile = createPileWithNumberOfCards(5);

            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withPlayers(createNumberOfPlayers(2))
                    .withHitRules(HitRulesFixtures.alwaysApplyingRules())
                    .withPile(pile)
                    .build();

            Player player = batailleCorse.getCurrentPlayer();

            batailleCorse.hit(player);

            assertThat(pile.isEmpty(), is(true));
            assertThat(player.getHandSize(), is(5));
        }

        @Test
        public void whenWinning_thenSetCurrentPlayerToWinningPlayer() {

            int nbCards = 5;
            Pile pile = createPileWithNumberOfCards(5);

            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withPlayers(createNumberOfPlayers(3))
                    .withHitRules(HitRulesFixtures.alwaysApplyingRules())
                    .withCurrentPlayer(0)
                    .withPile(pile)
                    .build();

            Player player = batailleCorse.getPlayerByIndex(2);

            batailleCorse.hit(player);

            assertThat(batailleCorse.getCurrentPlayer(), is(player));
        }

    }

}