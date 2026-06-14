package org.kevinkib.cardgames.bataillecorse.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.HonourCard;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.testhelpers.CardBuilder;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class HonourCardTest {

    @Test
    public void givenHonourCard_whenIsHonourCard_thenReturnsTrue() {
        Card card = CardBuilder.aCard().withRank(FrenchRank.KING).build();

        assertThat(HonourCard.isHonour(card), is(true));
    }

    @Test
    public void givenNotHonourCard_whenIsHonourCard_thenReturnsFalseTrue() {
        Card card = CardBuilder.aCard().withRank(FrenchRank.NINE).build();

        assertThat(HonourCard.isHonour(card), is(false));
    }

    @Test
    public void givenCardWithHonourRank_whenFrom_thenReturnHonourCard() {
        Card card = CardBuilder.aCard().withRank(FrenchRank.KING).build();

        assertThat(HonourCard.from(card), is(HonourCard.KING));
    }

    @Test
    public void givenCardWithoutHonourRank_whenFrom_thenThrowIllegalArgumentException() {
        Card card = CardBuilder.aCard().withRank(FrenchRank.EIGHT).build();

        assertThrows(IllegalArgumentException.class, () -> {
            HonourCard.from(card);
        });
    }

}