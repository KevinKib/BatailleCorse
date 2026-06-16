package org.kevinkib.cardgames.bullshit.presentation.dto;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class BullshitDtoStartedTest {

    @Test
    void givenGame_whenForViewer_thenStartedTrue() {
        Bullshit game = new Bullshit(GameId.generate(), 2);

        BullshitDto dto = BullshitDto.forViewer(game, new PlayerId(0));

        assertThat(dto.started(), is(true));
    }
}
