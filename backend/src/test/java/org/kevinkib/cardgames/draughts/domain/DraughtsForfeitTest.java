package org.kevinkib.cardgames.draughts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.BLACK;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.WHITE;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;

class DraughtsForfeitTest {

    @Test
    void theOtherSeatWinsByForfeit() {
        Draughts game = DraughtsGames.opening();

        game.forfeit(WHITE);

        assertThat(game.isFinished()).isTrue();
        assertThat(game.result()).isEqualTo(new DraughtsResult(BLACK, DraughtsResult.Reason.FORFEIT));
        assertThat(game.version()).isEqualTo(1);
    }

    @Test
    void aForfeitClearsAPendingOffer() throws Exception {
        Draughts game = DraughtsGames.opening();
        game.offerDraw(WHITE);

        game.forfeit(BLACK);

        assertThat(game.drawOffer()).isNull();
        assertThat(game.availableActions(WHITE)).isEmpty();
    }

    @Test
    void aSecondForfeitOrAnUnknownSeatChangesNothing() {
        Draughts game = DraughtsGames.opening();
        game.forfeit(WHITE);
        int version = game.version();

        game.forfeit(BLACK);
        game.forfeit(new PlayerId(7));

        assertThat(game.version()).isEqualTo(version);
        assertThat(game.result().winner()).isEqualTo(BLACK);
    }

    @Test
    void anUnknownSeatCannotForfeitARunningGame() {
        Draughts game = DraughtsGames.opening();

        game.forfeit(new PlayerId(7));

        assertThat(game.isFinished()).isFalse();
    }
}
