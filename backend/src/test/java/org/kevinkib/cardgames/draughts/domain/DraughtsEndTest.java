package org.kevinkib.cardgames.draughts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.BLACK;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.WHITE;

import java.util.List;
import org.junit.jupiter.api.Test;

class DraughtsEndTest {

    @Test
    void takingTheLastPieceWinsTheGame() throws Exception {
        Draughts game = DraughtsGames.from(Pos.white("w27", "b21"));

        game.move(WHITE, 27, 16, List.of(21));

        assertThat(game.isFinished()).isTrue();
        assertThat(game.phase()).isEqualTo(Phase.FINISHED);
        assertThat(game.result()).isEqualTo(new DraughtsResult(WHITE, DraughtsResult.Reason.NO_PIECES));
        assertThat(game.availableActions(WHITE)).isEmpty();
        assertThat(game.availableActions(BLACK)).isEmpty();
    }

    @Test
    void leavingTheOpponentWithoutALegalMoveWinsTheGame() throws Exception {
        // the black man on 45 can only go to 50, which is taken, and has nothing to capture
        Draughts game = DraughtsGames.from(Pos.white("w50", "w30", "b45"));

        game.move(WHITE, 30, 25, List.of());

        assertThat(game.result()).isEqualTo(new DraughtsResult(WHITE, DraughtsResult.Reason.BLOCKED));
        assertThat(game.isFinished()).isTrue();
    }

    @Test
    void aFinishedGameAcceptsNoMoreMoves() throws Exception {
        Draughts game = DraughtsGames.from(Pos.white("w27", "b21"));
        game.move(WHITE, 27, 16, List.of(21));
        int version = game.version();

        assertThatThrownBy(() -> game.move(BLACK, 1, 6, List.of())).isInstanceOf(FinishedGameException.class);

        assertThat(game.version()).isEqualTo(version);
        assertThat(game.legalMoves(WHITE)).isEmpty();
    }

    @Test
    void theGameOngoingHasNoResult() {
        Draughts game = DraughtsGames.opening();

        assertThat(game.result().isFinished()).isFalse();
        assertThat(game.phase()).isEqualTo(Phase.PLAY);
        assertThat(game.getPlayerIds()).containsExactly(WHITE, BLACK);
    }
}
