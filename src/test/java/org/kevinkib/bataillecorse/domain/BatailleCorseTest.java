package org.kevinkib.bataillecorse.domain;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.domain.penality.Penality;
import org.kevinkib.bataillecorse.domain.slaprules.SlapRulesFixtures;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.CardPileState;
import org.kevinkib.cards.domain.Hand;
import org.kevinkib.cards.testhelpers.CardFixtures;
import org.kevinkib.cards.testhelpers.HandBuilder;
import org.kevinkib.cards.testhelpers.HandFixtures;

import java.util.Arrays;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.kevinkib.bataillecorse.domain.BatailleCorseTest.InitializationTest.IsEveryCardHidden.everyCardHidden;
import static org.kevinkib.bataillecorse.domain.CentralPileFixtures.createCentralPileWithNumberOfCards;
import static org.kevinkib.bataillecorse.domain.PlayerFixtures.createNumberOfPlayers;
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
                    .buildAndInitialize();
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

        @Test
        public void givenFullPile_thenThrowFullCentralPileException() {
            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withPlayers(Arrays.asList(
                        PlayerBuilder.aPlayer()
                            .withHand(HandFixtures.createHandWithCards(CardFixtures.anyCard()))
                            .build(),
                        PlayerBuilder.aPlayer().build()
                    ))
                    .withCentralPile(CentralPileFixtures.createWithState(CentralPileState.FULL))
                    .build();

            Player player = batailleCorse.getCurrentPlayer();

            assertThrows(FullCentralPileException.class, () -> {
                batailleCorse.send(player);
            });
        }

    }

    @Nested
    class SlapTest {

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
        public void givenNoCardsInPile_thenThrowCannotSlapIfNoCardsInPileException() {

            CentralPile emptyCentralPile = CentralPileFixtures.createEmptyCentralPile();

            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                     .withPlayers(createNumberOfPlayers(2))
                    .withCentralPile(emptyCentralPile)
                    .build();

            Player player = batailleCorse.getCurrentPlayer();

            assertThrows(CannotSlapIfNoCardsInPileException.class, () -> {
                batailleCorse.slap(player);
            });
        }

        @Test
        public void whenLosing_thenApplyPenality() {

            Penality penality = mock(Penality.class);
            CentralPile pile = mock(CentralPile.class);

            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withPlayers(createNumberOfPlayers(2))
                    .withSlapRules(SlapRulesFixtures.neverApplyingRules())
                    .withPenality(penality)
                    .withCentralPile(pile)
                    .build();

            Player player = batailleCorse.getCurrentPlayer();

            assertDoesNotThrow(() -> {
                batailleCorse.slap(player);
            });

            verify(penality, times(1)).apply(player, pile);
        }

        @Test
        public void whenWinning_thenClearPile_andGivePileCardsToWinningPlayer() {

            int nbCards = 5;
            CentralPile pile = createCentralPileWithNumberOfCards(nbCards);

            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withPlayers(createNumberOfPlayers(2))
                    .withSlapRules(SlapRulesFixtures.alwaysApplyingRules())
                    .withCentralPile(pile)
                    .build();

            Player player = batailleCorse.getCurrentPlayer();

            assertDoesNotThrow(() -> {
                batailleCorse.slap(player);
            });

            assertThat(pile.isEmpty(), is(true));
            assertThat(player.getHandSize(), is(5));
        }

        @Test
        public void whenWinning_thenSetCurrentPlayerToWinningPlayer() {

            int nbCards = 5;
            CentralPile pile = createCentralPileWithNumberOfCards(nbCards);

            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withPlayers(createNumberOfPlayers(3))
                    .withSlapRules(SlapRulesFixtures.alwaysApplyingRules())
                    .withCurrentPlayer(0)
                    .withCentralPile(pile)
                    .build();

            Player player = batailleCorse.getPlayerByIndex(2);

            assertDoesNotThrow(() -> {
                batailleCorse.slap(player);
            });

            assertThat(batailleCorse.getCurrentPlayer(), is(player));
        }

    }

    @Nested
    class GrabTest {

        private BatailleCorse batailleCorse;
        private Player player;

        @BeforeEach
        public void beforeEach() {
            player = PlayerBuilder.aPlayer()
                    .withId(1)
                    .withHand(HandBuilder.aHand().withNoCards().build())
                    .build();
        }

        @Test
        public void givenNotGrabbablePile_thenThrowCannotGrabException() {
            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withCentralPile(CentralPileFixtures.createEmptyCentralPile())
                    .withPlayers(Arrays.asList(player))
                    .build();

            assertThrows(CannotGrabException.class, () -> {
                batailleCorse.grab(player);
            });
        }

        @Test
        public void givenGrabbablePile_thenClearPileAndGiveCardsToPlayer() {
            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withCentralPile(CentralPileFixtures.createCentralPileGrabbableByPlayer(player))
                    .withPlayers(Arrays.asList(player))
                    .build();

            int pileSize = batailleCorse.getPileSize();

            assertDoesNotThrow(() -> {
                batailleCorse.grab(player);
            });

            assertThat(batailleCorse.getPileSize(), is(0));
            assertThat(player.getHandSize(), is(pileSize));
        }

        @Test
        public void givenGrabbablePile_thenReverseCardsFromPileInHand() {
            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withCentralPile(CentralPileFixtures.createCentralPileGrabbableByPlayer(player))
                    .withPlayers(Arrays.asList(player))
                    .build();

            int pileSize = batailleCorse.getPileSize();

            assertDoesNotThrow(() -> {
                batailleCorse.grab(player);
            });

            assertThat(batailleCorse.getPileSize(), is(0));
            assertThat(player.getHandSize(), is(pileSize));
        }


    }

}