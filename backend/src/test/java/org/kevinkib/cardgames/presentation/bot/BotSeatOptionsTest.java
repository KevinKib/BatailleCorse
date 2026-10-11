package org.kevinkib.cardgames.presentation.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BotSeatOptionsTest {

    @Test
    void noneAnswersNoOptionForAnySeat() {
        assertTrue(BotSeatOptions.none().seatOptions(GameId.generate(), new PlayerId(1)).isEmpty());
    }
}
