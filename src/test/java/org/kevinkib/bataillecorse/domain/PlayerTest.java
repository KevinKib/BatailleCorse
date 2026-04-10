package org.kevinkib.bataillecorse.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.testhelpers.CardBuilder;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class PlayerTest {

    @Test
    public void givenCards_whenAddCardsFromPile_thenReversePileCards() {
        Player player = PlayerBuilder.aPlayer().withEmptyHand().build();
        Card two = CardBuilder.aCard().withRank(FrenchRank.TWO).build();
        Card three = CardBuilder.aCard().withRank(FrenchRank.THREE).build();

        List<Card> cards = Arrays.asList(two, three);

        player.addCardsFromPile(cards);

        assertThat(player.hand().getCards(), is(Arrays.asList(three, two)));
    }

}
