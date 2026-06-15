package org.kevinkib.cardgames.bullshit.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.presentation.dto.CardDto;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cards.domain.Card;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BullshitCardMapperTest {

    @Test
    void givenDealtCard_whenRoundTripped_thenEqualsAndPossessed() {
        Bullshit game = (Bullshit) new BullshitFactory().create(GameId.generate(), 2);
        Card original = game.getPlayers().get(0).getCards().get(0);

        Card roundTrip = BullshitCardMapper.toCard(CardDto.from(original));

        assertThat(roundTrip, is(original));
        assertThat(game.getPlayers().get(0).possessesAll(List.of(roundTrip)), is(true));
    }

    @Test
    void givenUnknownLabels_whenToCard_thenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> BullshitCardMapper.toCard(new CardDto("NOPE", "NOPE", "NOPE_NOPE")));
    }
}
