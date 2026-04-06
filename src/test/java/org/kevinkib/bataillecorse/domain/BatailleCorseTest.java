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
import org.kevinkib.cards.domain.french.FrenchRank;
import org.kevinkib.cards.testhelpers.CardBuilder;
import org.kevinkib.cards.testhelpers.CardFixtures;
import org.kevinkib.cards.testhelpers.HandBuilder;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.kevinkib.bataillecorse.domain.BatailleCorseFixtures.createFinishedGame;
import static org.kevinkib.bataillecorse.domain.BatailleCorseTest.InitializationTest.IsEveryCardHidden.everyCardHidden;
import static org.kevinkib.bataillecorse.domain.CentralPileFixtures.createCentralPileWithNumberOfCards;
import static org.kevinkib.bataillecorse.domain.PlayerFixtures.*;
import static org.kevinkib.bataillecorse.domain.slaprules.SlapRulesFixtures.alwaysApplyingSlapRules;
import static org.kevinkib.bataillecorse.domain.slaprules.SlapRulesFixtures.anySlapRules;
import static org.kevinkib.cards.domain.french.FrenchRank.JACK;
import static org.kevinkib.cards.testhelpers.CardFixtures.anyCard;
import static org.mockito.Mockito.*;

class BatailleCorseTest {

    private int nbPlayers;

    @Nested
    class InitializationTest {

        @BeforeEach
        public void beforeEach() {
            nbPlayers = 2;
        }

        @Test
        public void whenInitializing_thenGiveHandsToPlayers_withHiddenCards() {
            BatailleCorse batailleCorse = new BatailleCorse(nbPlayers);

            assertNotNull(batailleCorse.getPlayerByIndex(0).hand());
            assertNotNull(batailleCorse.getPlayerByIndex(1).hand());

            assertThat(batailleCorse.getPlayerByIndex(0).hand(), is(everyCardHidden()));
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

            public static Matcher<Hand> everyCardHidden() {
                return new IsEveryCardHidden();
            }
        }
    }

    @Nested
    class SendTest {

        private BatailleCorse batailleCorse;

        @BeforeEach
        public void beforeEach() {
            int nbPlayers = 2;
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
                        PlayerBuilder.aPlayer().withNonEmptyHand().build(),
                        PlayerBuilder.aPlayer().withNonEmptyHand().build()
                    ))
                    .withCentralPile(CentralPileFixtures.createWithState(CentralPileState.FULL))
                    .build();

            Player player = batailleCorse.getCurrentPlayer();

            assertThrows(FullCentralPileException.class, () -> {
                batailleCorse.send(player);
            });
        }

        @Test
        public void givenFinishedGame_thenThrowFinishedGameException() {
            batailleCorse = createFinishedGame();

            assertThrows(FinishedGameException.class, () -> {
                batailleCorse.send(PlayerBuilder.aPlayer().build());
            });
        }

        @Test
        public void givenThreePlayers_anHonourCardInPile_andAPlayerDoesNotHaveEnoughCards_thenNextPlayerFinishes() {
            Player player0 = PlayerBuilder.aPlayer().withId(0).withCards(
                    CardBuilder.aCard().withRank(FrenchRank.ACE).build()
            ).build();
            Player player1 = PlayerBuilder.aPlayer().withId(1).withCards(
                    anyCard(), anyCard()
            ).build();
            Player player2 = PlayerBuilder.aPlayer().withId(2).withCards(
                    CardFixtures.createNumberOfCards(5).toArray(new Card[]{})
            ).build();

            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withPlayers(Arrays.asList(
                            player0, player1, player2
                    ))
                    .withCentralPile(CentralPileFixtures.createEmptyCentralPile())
                    .withSlapRules(anySlapRules())
                    .build();

            assertDoesNotThrow(() -> {
                batailleCorse.send(player0); // Ace

                batailleCorse.send(player1);
                batailleCorse.send(player1);

                assertThat(player1.hasAnyCards(), is(false));
                assertThat(batailleCorse.getCurrentPlayer(), is(player2));

                batailleCorse.send(player2);
            });
        }
    }

    @Nested
    class SlapTest {

        private BatailleCorse batailleCorse;

        @BeforeEach
        public void beforeEach() {
            nbPlayers = 2;
        }

        @Test
        public void givenNoCardsInPile_thenThrowCannotSlapIfNoCardsInPileException() {

            CentralPile emptyCentralPile = CentralPileFixtures.createEmptyCentralPile();

            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withPlayers(createNumberOfPlayersWithAnyCards(2))
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
                    .withPlayers(createNumberOfPlayersWithAnyCards(2))
                    .withSlapRules(SlapRulesFixtures.neverApplyingSlapRules())
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
        public void whenLosing_thenReturnFalse() {

            Penality penality = mock(Penality.class);
            CentralPile pile = mock(CentralPile.class);

            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withPlayers(createNumberOfPlayersWithAnyCards(2))
                    .withSlapRules(SlapRulesFixtures.neverApplyingSlapRules())
                    .withPenality(penality)
                    .withCentralPile(pile)
                    .build();

            Player player = batailleCorse.getCurrentPlayer();

            assertDoesNotThrow(() -> {
                boolean successfulSlap = batailleCorse.slap(player);
                assertThat(successfulSlap, is(false));
            });
        }

        @Test
        public void whenWinning_thenClearPile_andGivePileCardsToWinningPlayer() {

            int nbCards = 5;
            CentralPile pile = createCentralPileWithNumberOfCards(nbCards);

            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withPlayers(createNumberOfPlayersWithOneCard(2))
                    .withSlapRules(alwaysApplyingSlapRules())
                    .withCentralPile(pile)
                    .build();

            Player player = batailleCorse.getCurrentPlayer();

            assertDoesNotThrow(() -> {
                batailleCorse.slap(player);
            });

            int expectedNumberOfCards = nbCards + 1;
            assertThat(pile.isEmpty(), is(true));
            assertThat(player.getHandSize(), is(expectedNumberOfCards));
        }

        @Test
        public void whenWinning_thenSetCurrentPlayerToWinningPlayer() {

            int nbCards = 5;
            CentralPile pile = createCentralPileWithNumberOfCards(nbCards);

            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withPlayers(createNumberOfPlayersWithAnyCards(3))
                    .withSlapRules(alwaysApplyingSlapRules())
                    .withCurrentPlayer(0)
                    .withCentralPile(pile)
                    .build();

            Player player = batailleCorse.getPlayerByIndex(2);

            assertDoesNotThrow(() -> {
                batailleCorse.slap(player);
            });

            assertThat(batailleCorse.getCurrentPlayer(), is(player));
        }

        @Test
        public void whenWinning_thenReturnTrue() {

            int nbCards = 5;
            CentralPile pile = createCentralPileWithNumberOfCards(nbCards);

            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withPlayers(createNumberOfPlayersWithOneCard(2))
                    .withSlapRules(alwaysApplyingSlapRules())
                    .withCentralPile(pile)
                    .build();

            Player player = batailleCorse.getCurrentPlayer();

            assertDoesNotThrow(() -> {
                boolean successfulSlap = batailleCorse.slap(player);
                assertThat(successfulSlap, is(true));
            });
        }

        @Test
        public void givenFinishedGame_thenThrowFinishedGameException() {
            batailleCorse = createFinishedGame();

            assertThrows(FinishedGameException.class, () -> {
                batailleCorse.slap(PlayerBuilder.aPlayer().build());
            });
        }

    }

    @Nested
    class GrabTest {

        private BatailleCorse batailleCorse;
        private Player player;
        private final int nbInitialCardsForPlayer = 1;

        @BeforeEach
        public void beforeEach() {
            player = PlayerBuilder.aPlayer()
                    .withId(1)
                    .withHand(HandBuilder.aHand()
                            .withCards(CardFixtures.createNumberOfCards(nbInitialCardsForPlayer))
                            .build())
                    .build();
        }

        @Test
        public void givenNotGrabbablePile_thenThrowCannotGrabException() {
            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withCentralPile(CentralPileFixtures.createEmptyCentralPile())
                    .withPlayers(Arrays.asList(
                            player,
                            PlayerBuilder.aPlayer().withNonEmptyHand().build()
                    ))
                    .build();

            assertThrows(CannotGrabException.class, () -> {
                batailleCorse.grab(player);
            });
        }

        @Test
        public void givenGrabbablePile_thenClearPileAndGiveCardsToPlayer() {
            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withCentralPile(CentralPileFixtures.createCentralPileGrabbableByPlayer(player))
                    .withPlayers(Collections.singletonList(player))
                    .withSlapRules(anySlapRules())
                    .build();

            int pileSize = batailleCorse.getPileSize();

            assertDoesNotThrow(() -> {
                batailleCorse.grab(player);
            });

            assertThat(batailleCorse.getPileSize(), is(0));
            assertThat(player.getHandSize(), is(pileSize + nbInitialCardsForPlayer));
        }

        @Test
        public void givenGrabbablePile_thenReverseCardsFromPileInHand() {
            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withCentralPile(CentralPileFixtures.createCentralPileGrabbableByPlayer(player))
                    .withPlayers(Collections.singletonList(player))
                    .withSlapRules(anySlapRules())
                    .build();

            int pileSize = batailleCorse.getPileSize();

            assertDoesNotThrow(() -> {
                batailleCorse.grab(player);
            });

            assertThat(batailleCorse.getPileSize(), is(0));
            assertThat(player.getHandSize(), is(pileSize + nbInitialCardsForPlayer));
        }

        @Test
        public void givenFinishedGame_thenThrowFinishedGameException() {
            batailleCorse = createFinishedGame();

            assertThrows(FinishedGameException.class, () -> {
                batailleCorse.grab(PlayerBuilder.aPlayer().build());
            });
        }

        @Test
        public void givenGrabbablePile_thenNextPlayerIsPlayerThatGrabbed() {

            Player player1 = PlayerBuilder.aPlayer().withId(1).withCardsWithRanks(JACK).build();
            Player player2 = PlayerBuilder.aPlayer().withId(2).withCards(anyCard()).build();
            Player player3 = PlayerBuilder.aPlayer().withId(3).withCards(anyCard()).build();

            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withCentralPile(CentralPileFixtures.createEmptyCentralPile())
                    .withPlayers(Arrays.asList(
                            player1, player2, player3)
                    )
                    .withSlapRules(anySlapRules())
                    .build();


            assertDoesNotThrow(() -> {
                batailleCorse.send(player1); // Jack
                batailleCorse.send(player2); // Any

                batailleCorse.grab(player1);
            });

            assertThat(batailleCorse.getCurrentPlayer(), is(player1));
        }

    }

    @Nested
    class GetAvailableActionsTest {

        private BatailleCorse batailleCorse;
        private Player player1;

        @BeforeEach
        public void beforeEach() {
            int nbPlayers = 2;

            player1 = PlayerBuilder.aPlayer().withId(1).build();

            batailleCorse = BatailleCorseBuilder.aBatailleCorse()
                    .withPlayers(Arrays.asList(
                            player1,
                            PlayerBuilder.aPlayer().withId(2).build()
                    ))
                    .buildAndInitialize();
        }

        @Test
        void givenFinishedGame_thenNoActionsAreAvailable() {
            batailleCorse = BatailleCorseFixtures.createFinishedGame();
            assertThat(batailleCorse.getAvailableActions(player1).isEmpty(), is(true));
        }

    }

}