package org.kevinkib.cardgames.bullshit.presentation.dto;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class BullshitDtoNamesTest {

    private final Bullshit game = new Bullshit(GameId.generate(), 3);

    @Test
    void givenSeatNames_whenForViewer_thenEachPlayerCarriesItsTrimmedName() {
        BullshitDto dto = BullshitDto.forViewer(game, new PlayerId(0),
                Map.of(new PlayerId(0), " Alice ", new PlayerId(1), "Bob"), Set.of());

        assertThat(dto.players().get(0).name(), is("Alice"));
        assertThat(dto.players().get(1).name(), is("Bob"));
    }

    @Test
    void givenMissingOrBlankName_whenForViewer_thenNameIsNull() {
        BullshitDto dto = BullshitDto.forViewer(game, new PlayerId(0),
                Map.of(new PlayerId(1), "   "), Set.of());

        assertThat(dto.players().get(1).name(), is(nullValue()));
        assertThat(dto.players().get(2).name(), is(nullValue()));
    }

    @Test
    void givenBotSeat_whenForViewer_thenFlaggedAndNeverNamed() {
        BullshitDto dto = BullshitDto.forViewer(game, new PlayerId(0),
                Map.of(new PlayerId(2), "Bot 1"), Set.of(new PlayerId(2)));

        assertThat(dto.players().get(2).bot(), is(true));
        assertThat(dto.players().get(2).name(), is(nullValue()));
        assertThat(dto.players().get(0).bot(), is(false));
    }
}
