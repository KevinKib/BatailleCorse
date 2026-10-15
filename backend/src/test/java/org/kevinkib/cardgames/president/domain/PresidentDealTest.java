package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.codes;

class PresidentDealTest {

    private static President president(int players, long seed) {
        return new President(GameId.generate(), players, seed);
    }

    private static List<Card> allHands(President game, int players) {
        List<Card> all = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            all.addAll(game.hand(new PlayerId(seat)));
        }
        return all;
    }

    @ParameterizedTest(name = "{0} players: hand sizes {1}")
    @CsvSource(delimiter = '|', value = {
            "3 | 18 17 17",
            "4 | 13 13 13 13",
            "5 | 11 11 10 10 10",
            "6 | 9 9 9 9 8 8",
            "7 | 8 8 8 7 7 7 7",
            "8 | 7 7 7 7 6 6 6 6",
    })
    void everyCardIsDealtAndTheExtraCardsGoToTheSeatsAfterTheDealer(int players, String sizes) {
        President game = president(players, 7L);

        List<Integer> expected = Arrays.stream(sizes.split(" ")).map(Integer::parseInt).toList();
        assertThat(game.handSizes(), is(expected));
        List<Card> all = allHands(game, players);
        assertThat(all.size(), is(52));
        assertThat(new HashSet<>(all).size(), is(52));
    }

    @Test
    void theDealerOfRoundOneIsTheLastSeatAndTheGameStartsInPlay() {
        President game = president(5, 1L);

        assertThat(game.dealer(), is(new PlayerId(4)));
        assertThat(game.round(), is(1));
        assertThat(game.phase(), is(Phase.PLAYING));
        assertThat(game.isFinished(), is(false));
        assertThat(game.getPlayerIds(), is(List.of(new PlayerId(0), new PlayerId(1), new PlayerId(2),
                new PlayerId(3), new PlayerId(4))));
    }

    @Test
    void handsAreSortedFromWeakestToStrongest() {
        President game = president(4, 3L);

        for (PlayerId seat : game.getPlayerIds()) {
            List<Card> hand = game.hand(seat);
            assertThat(hand, is(org.kevinkib.cardgames.president.domain.card.HandSort.sorted(hand,
                    org.kevinkib.cardgames.president.domain.card.RankOrder.standard())));
        }
    }

    @Test
    void sameSeedDealsTheSameHandsAndAnotherSeedDoesNot() {
        assertThat(president(5, 42L).hand(new PlayerId(0)), is(president(5, 42L).hand(new PlayerId(0))));
        assertThat(president(5, 42L).hand(new PlayerId(0)), not(is(president(5, 43L).hand(new PlayerId(0)))));
    }

    @Test
    void cardsAreDealtOneAtATimeStartingWithTheSeatAfterTheDealer() {
        ScriptedDeckSource source = new ScriptedDeckSource(new RandomDeckSource());
        GameId id = GameId.generate();
        source.fix(id, 1, cards("3C 4C 5C 6C 7C 8C"));

        President game = new President(id, 3, 1L, source);

        assertThat(game.hand(new PlayerId(0)), hasItems(cards("3C 6C").toArray(new Card[0])));
        assertThat(game.hand(new PlayerId(1)), hasItems(cards("4C 7C").toArray(new Card[0])));
        assertThat(game.hand(new PlayerId(2)), hasItems(cards("5C 8C").toArray(new Card[0])));
    }

    @Test
    void anyDeckSourceDecidesTheDealOrder() {
        List<Card> sortedDeck = new ArrayList<>(new RandomDeckSource().deck(GameId.generate(), 1, 1L, DeckSpec.standard()));
        sortedDeck.sort(java.util.Comparator.comparing(PresidentFixtures::code));
        DeckSource fixed = (id, round, seed, spec) -> sortedDeck;

        President game = new President(GameId.generate(), 4, 1L, fixed);

        Set<Card> seatZero = new HashSet<>(game.hand(new PlayerId(0)));
        Set<Card> expected = new HashSet<>();
        for (int i = 0; i < 52; i += 4) {
            expected.add(sortedDeck.get(i));
        }
        assertThat(seatZero, is(expected));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 2, 9, 10})
    void outsideThreeToEightPlayersThereIsNoGame(int players) {
        assertThrows(IllegalArgumentException.class, () -> president(players, 1L));
    }

    @Test
    void aDeckSourceThatDoesNotReturnTheWholeDeckIsRefused() {
        DeckSource short1 = (id, round, seed, spec) -> cards("3C 4C 5C");

        assertThrows(IllegalArgumentException.class, () -> new President(GameId.generate(), 3, 1L, short1));
    }

    @Test
    void deckSourcesDescribedInTheSpecReturnTheRightNumberOfCards() {
        RandomDeckSource random = new RandomDeckSource();
        GameId id = GameId.generate();

        assertThat(random.deck(id, 1, 5L, DeckSpec.standard()).size(), is(52));
        assertThat(random.deck(id, 1, 5L, new DeckSpec(1, true)).size(), is(54));
        assertThat(random.deck(id, 1, 5L, new DeckSpec(2, false)).size(), is(104));
        assertThat(random.deck(id, 1, 5L, new DeckSpec(2, true)).size(), is(108));
        assertThat(codes(random.deck(id, 1, 5L, DeckSpec.standard())),
                is(codes(random.deck(id, 1, 5L, DeckSpec.standard()))));
    }

    @Test
    void aScriptedSourceFixesOnlyTheRoundAndGameItWasToldAbout() {
        ScriptedDeckSource source = new ScriptedDeckSource(new RandomDeckSource());
        GameId id = GameId.generate();
        source.fix(id, 1, cards("3C 4C"));

        assertThat(codes(source.deck(id, 1, 9L, DeckSpec.standard()).subList(0, 2)), is("3C 4C"));
        assertThat(source.deck(id, 1, 9L, DeckSpec.standard()).size(), is(52));
        assertThat(source.deck(id, 2, 9L, DeckSpec.standard()),
                is(new RandomDeckSource().deck(id, 2, 9L, DeckSpec.standard())));
        source.clear(id);
        assertThat(source.deck(id, 1, 9L, DeckSpec.standard()),
                is(new RandomDeckSource().deck(id, 1, 9L, DeckSpec.standard())));
        assertThrows(IllegalArgumentException.class, () -> source.fix(id, 0, cards("3C")));
    }
}
