package org.kevinkib.cardgames.kobo.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

class KoboFixedDealTest {

    private final ScriptedDeckSource decks = new ScriptedDeckSource(new RandomDeckSource());
    private final GameId id = GameId.generate();

    private List<Card> cards(String... codes) {
        return Stream.of(codes).map(KoboFixtures::card).toList();
    }

    private List<Card> hand(Kobo kobo, int seat) {
        return kobo.slots(new PlayerId(seat)).stream().map(s -> s.card()).toList();
    }

    private void playToRoundTwo(Kobo kobo) throws Exception {
        for (PlayerId p : kobo.getPlayerIds()) {
            kobo.ready(p);
        }
        boolean announced = false;
        while (kobo.phase() != Phase.ROUND_OVER) {
            PlayerId seat = kobo.currentSeat();
            switch (kobo.phase()) {
                case DRAW -> kobo.draw(seat);
                case DECISION -> kobo.discardDrawn(seat);
                case POWER, KING_DECISION -> kobo.skipPower(seat);
                case TURN_END -> {
                    if (!announced) {
                        announced = true;
                        kobo.announceKobo(seat);
                    } else {
                        kobo.endTurn(seat);
                    }
                }
                default -> throw new IllegalStateException(kobo.phase().name());
            }
        }
        for (PlayerId p : kobo.getPlayerIds()) {
            kobo.ready(p);
        }
    }

    @Test
    void givenAFixedRoundOne_whenDealt_thenCardsGoToSeatsInOrderThenTheDrawPile() throws Exception {
        decks.fix(id, 1, cards("KH", "KD", "KC", "KS", "7H", "7D", "7C", "7S", "AH"));

        Kobo kobo = new Kobo(id, 2, 1L, decks);

        assertThat(hand(kobo, 0), contains(cards("KH", "KD", "KC", "KS").toArray()));
        assertThat(hand(kobo, 1), contains(cards("7H", "7D", "7C", "7S").toArray()));
        assertThat(kobo.drawPileSize(), is(44));
        assertThat(kobo.cardsInPlay(), is(52));
        assertThat(kobo.phase(), is(Phase.MEMORISING));
    }

    @Test
    void givenAFixedPrefix_thenTheTopOfTheDrawPileFollowsTheFixedCards() throws Exception {
        decks.fix(id, 1, cards("2H", "2D", "2C", "2S", "3H", "3D", "3C", "3S", "KH", "QH"));
        Kobo kobo = new Kobo(id, 2, 1L, decks);
        kobo.ready(new PlayerId(0));
        kobo.ready(new PlayerId(1));

        kobo.draw(new PlayerId(0));
        assertThat(kobo.drawnCard(new PlayerId(0)).orElseThrow(), is(card("KH")));
    }

    @Test
    void givenAFixedDeck_thenAllFiftyTwoCardsStayDistinct() {
        decks.fix(id, 1, cards("AS", "AH"));
        Kobo kobo = new Kobo(id, 6, 3L, decks);

        List<Card> all = new ArrayList<>();
        for (int s = 0; s < 6; s++) {
            all.addAll(hand(kobo, s));
        }
        assertThat(new HashSet<>(all).size(), is(24));
        assertThat(hand(kobo, 0).subList(0, 2), contains(card("AS"), card("AH")));
        assertThat(kobo.cardsInPlay(), is(52));
    }

    @Test
    void givenNothingFixedForTheGame_thenTheDealIsRandomAndSeeded() {
        Kobo a = new Kobo(id, 3, 5L, decks);
        Kobo b = new Kobo(GameId.generate(), 3, 5L, decks);
        Kobo c = new Kobo(GameId.generate(), 3, 6L, decks);

        assertThat(hand(a, 0), is(hand(b, 0)));
        assertThat(hand(a, 0).equals(hand(c, 0)) && hand(a, 1).equals(hand(c, 1)), is(false));
    }

    @Test
    void givenARoundTwoFixed_thenRoundOneStaysRandomAndRoundTwoUsesTheFixedDeal() throws Exception {
        decks.fix(id, 2, cards("JH", "JD", "JC", "JS", "10H", "10D", "10C", "10S"));
        Kobo kobo = new Kobo(id, 2, 1L, decks);
        assertThat(hand(kobo, 0).equals(cards("JH", "JD", "JC", "JS")), is(false));

        playToRoundTwo(kobo);

        assertThat(kobo.round(), is(2));
        assertThat(hand(kobo, 0), contains(cards("JH", "JD", "JC", "JS").toArray()));
        assertThat(hand(kobo, 1), contains(cards("10H", "10D", "10C", "10S").toArray()));
    }

    @Test
    void givenADuplicateCard_whenFixing_thenItIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> decks.fix(id, 1, cards("KH", "KH")));
    }

    @Test
    void givenMoreThanFiftyTwoCardsOrANonPositiveRound_whenFixing_thenItIsRefused() {
        List<Card> tooMany = new ArrayList<>(new RandomDeckSource().deck(id, 1, 1L));
        tooMany.add(card("KH"));
        assertThrows(IllegalArgumentException.class, () -> decks.fix(id, 1, tooMany));
        assertThrows(IllegalArgumentException.class, () -> decks.fix(id, 0, cards("KH")));
    }

    @Test
    void givenAClearedGame_thenItDealsRandomAgain() {
        decks.fix(id, 1, cards("KH", "KD", "KC", "KS"));
        decks.clear(id);

        Kobo kobo = new Kobo(id, 2, 1L, decks);

        assertThat(hand(kobo, 0).equals(cards("KH", "KD", "KC", "KS")), is(false));
    }

    @Test
    void theDefaultConstructorsStillDealRandomly() {
        Kobo a = new Kobo(id, 2, 9L);
        Kobo b = new Kobo(id, 2, 9L, new RandomDeckSource());

        assertThat(hand(a, 0), is(hand(b, 0)));
        assertThat(hand(a, 1), is(hand(b, 1)));
        assertThat(new KoboFactory().create(id, 2), org.hamcrest.Matchers.instanceOf(Kobo.class));
    }
}
