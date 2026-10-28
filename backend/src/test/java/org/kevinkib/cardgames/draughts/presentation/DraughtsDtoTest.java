package org.kevinkib.cardgames.draughts.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.BLACK;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.WHITE;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.DraughtsGames;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;
import org.kevinkib.cardgames.draughts.presentation.dto.DraughtsDto;

class DraughtsDtoTest {

    private static DraughtsPosition layout(Colour toMove, String... specs) {
        char[] chars = ".".repeat(50).toCharArray();
        for (String spec : specs) {
            chars[Integer.parseInt(spec.substring(1)) - 1] = spec.charAt(0);
        }
        return DraughtsPosition.fromLayout(new String(chars), toMove);
    }

    @Test
    void theOpeningAsSeenByTheSeatOnTurn() {
        Draughts game = DraughtsGames.opening();

        DraughtsDto dto = DraughtsDto.forViewer(game, WHITE);

        assertThat(dto.started()).isTrue();
        assertThat(dto.id()).isEqualTo(game.getId().uuid().toString());
        assertThat(dto.gameType()).isEqualTo("draughts");
        assertThat(dto.version()).isZero();
        assertThat(dto.phase()).isEqualTo("PLAY");
        assertThat(dto.currentSeat()).isZero();
        assertThat(dto.whiteSeat()).isZero();
        assertThat(dto.board()).hasSize(50);
        assertThat(dto.board().get(0)).isEqualTo("b");
        assertThat(dto.board().get(19)).isEqualTo("b");
        assertThat(dto.board().get(20)).isNull();
        assertThat(dto.board().get(29)).isNull();
        assertThat(dto.board().get(30)).isEqualTo("w");
        assertThat(dto.board().get(49)).isEqualTo("w");
        assertThat(dto.availableActions()).containsExactly("MOVE", "OFFER_DRAW");
        assertThat(dto.legalMoves()).hasSize(9);
        assertThat(dto.legalMoves().get(0).captures()).isEmpty();
        assertThat(dto.captureRequired()).isFalse();
        assertThat(dto.maxCaptures()).isZero();
        assertThat(dto.lastMove()).isNull();
        assertThat(dto.drawOffer()).isNull();
        assertThat(dto.drawCounters()).isNull();
        assertThat(dto.repetitions()).isNull();
        assertThat(dto.history()).isEmpty();
        assertThat(dto.outcome()).isEqualTo(new DraughtsDto.Ongoing());
    }

    @Test
    void thePlayersCarryTheirColourAndCounts() {
        Draughts game = DraughtsGames.opening();

        DraughtsDto dto = DraughtsDto.forViewer(game, WHITE, Map.of(WHITE, "Alice"), Set.of(BLACK),
                Map.of(BLACK, "expert"));

        DraughtsDto.PlayerDto white = dto.players().get(0);
        assertThat(white.seat()).isZero();
        assertThat(white.name()).isEqualTo("Alice");
        assertThat(white.colour()).isEqualTo("WHITE");
        assertThat(white.current()).isTrue();
        assertThat(white.men()).isEqualTo(20);
        assertThat(white.kings()).isZero();
        assertThat(white.captured()).isZero();
        assertThat(white.bot()).isFalse();
        assertThat(white.level()).isNull();
        DraughtsDto.PlayerDto black = dto.players().get(1);
        assertThat(black.name()).isNull();
        assertThat(black.colour()).isEqualTo("BLACK");
        assertThat(black.current()).isFalse();
        assertThat(black.bot()).isTrue();
        assertThat(black.level()).isEqualTo("expert");
    }

    @Test
    void theSeatNotOnTurnGetsNoMovesButMayStillOfferADraw() {
        Draughts game = DraughtsGames.opening();

        DraughtsDto dto = DraughtsDto.forViewer(game, BLACK);

        assertThat(dto.legalMoves()).isEmpty();
        assertThat(dto.captureRequired()).isFalse();
        assertThat(dto.availableActions()).containsExactly("OFFER_DRAW");
    }

    @Test
    void aCompulsoryCaptureIsHintedToTheSeatOnTurnOnly() {
        Draughts game = DraughtsGames.from(layout(Colour.WHITE, "w32", "b28", "b19", "b1"));

        DraughtsDto white = DraughtsDto.forViewer(game, WHITE);
        DraughtsDto black = DraughtsDto.forViewer(game, BLACK);

        assertThat(white.captureRequired()).isTrue();
        assertThat(white.maxCaptures()).isEqualTo(2);
        assertThat(white.legalMoves()).hasSize(1);
        DraughtsDto.MoveDto move = white.legalMoves().get(0);
        assertThat(move.from()).isEqualTo(32);
        assertThat(move.to()).isEqualTo(14);
        assertThat(move.path()).containsExactly(23, 14);
        assertThat(move.captures()).containsExactly(28, 19);
        assertThat(move.promotes()).isFalse();
        assertThat(black.captureRequired()).isFalse();
        assertThat(black.maxCaptures()).isZero();
    }

    @Test
    void theLastMoveAndTheHistoryFollowTheGame() throws Exception {
        Draughts game = DraughtsGames.opening();
        game.move(WHITE, 32, 28, List.of());
        game.move(BLACK, 17, 22, List.of());

        DraughtsDto dto = DraughtsDto.forViewer(game, WHITE);

        assertThat(dto.history()).containsExactly("32-28", "17-22");
        assertThat(dto.lastMove().seat()).isEqualTo(1);
        assertThat(dto.lastMove().from()).isEqualTo(17);
        assertThat(dto.lastMove().to()).isEqualTo(22);
        assertThat(dto.lastMove().path()).containsExactly(22);
        assertThat(dto.version()).isEqualTo(2);
        assertThat(dto.currentSeat()).isZero();
    }

    @Test
    void aPendingOfferIsShownToBothSeatsWithTheAnswersForTheOpponent() throws Exception {
        Draughts game = DraughtsGames.opening();
        game.offerDraw(BLACK);

        DraughtsDto forWhite = DraughtsDto.forViewer(game, WHITE);
        DraughtsDto forBlack = DraughtsDto.forViewer(game, BLACK);

        assertThat(forWhite.drawOffer()).isEqualTo(new DraughtsDto.DrawOfferDto(1));
        assertThat(forBlack.drawOffer()).isEqualTo(new DraughtsDto.DrawOfferDto(1));
        assertThat(forWhite.availableActions()).containsExactly("MOVE", "ACCEPT_DRAW", "DECLINE_DRAW");
        assertThat(forBlack.availableActions()).isEmpty();
    }

    @Test
    void kingsOnlyPliesAreShownFromThirty() throws Exception {
        Draughts game = DraughtsGames.from(layout(Colour.WHITE, "W50", "W49", "B1", "B2"));
        DraughtsGames.playQuietPlies(game, 29);

        assertThat(DraughtsDto.forViewer(game, WHITE).drawCounters()).isNull();

        DraughtsGames.playQuietPlies(game, 1);
        DraughtsDto.DrawCountersDto counters = DraughtsDto.forViewer(game, WHITE).drawCounters();

        assertThat(counters.kingOnlyPlies()).isEqualTo(30);
        assertThat(counters.kingOnlyLimit()).isEqualTo(50);
        assertThat(counters.endgame()).isNull();
    }

    @Test
    void theEndgameCountIsShownAsSoonAsTheMaterialMatches() throws Exception {
        Draughts game = DraughtsGames.from(layout(Colour.WHITE, "W50", "W49", "W48", "B1"));
        DraughtsGames.playQuietPlies(game, 2);

        DraughtsDto.DrawCountersDto counters = DraughtsDto.forViewer(game, BLACK).drawCounters();

        assertThat(counters.endgame().endgameCase()).isEqualTo("SIXTEEN");
        assertThat(counters.endgame().plies()).isEqualTo(2);
        assertThat(counters.endgame().limit()).isEqualTo(32);
        assertThat(counters.kingOnlyPlies()).isNull();
    }

    @Test
    void theRepetitionCountIsShownFromTwo() throws Exception {
        Draughts game = DraughtsGames.from(layout(Colour.WHITE, "W50", "W49", "B1", "B2"));
        DraughtsGames.play(game, "50-44");
        DraughtsGames.play(game, "2-7");
        DraughtsGames.play(game, "44-50");
        assertThat(DraughtsDto.forViewer(game, WHITE).repetitions()).isNull();

        DraughtsGames.play(game, "7-2");

        assertThat(DraughtsDto.forViewer(game, WHITE).repetitions()).isEqualTo(2);
    }

    @Test
    void aFinishedGameCarriesItsOutcomeAndNoActions() {
        Draughts game = DraughtsGames.opening();
        game.forfeit(WHITE);

        DraughtsDto dto = DraughtsDto.forViewer(game, BLACK);

        assertThat(dto.phase()).isEqualTo("FINISHED");
        assertThat(dto.outcome()).isEqualTo(new DraughtsDto.Finished(1, "FORFEIT"));
        assertThat(dto.availableActions()).isEmpty();
        assertThat(dto.legalMoves()).isEmpty();
    }

    @Test
    void aDrawHasNoWinner() throws Exception {
        Draughts game = DraughtsGames.opening();
        game.offerDraw(WHITE);
        game.acceptDraw(BLACK);

        assertThat(DraughtsDto.forViewer(game, WHITE).outcome()).isEqualTo(new DraughtsDto.Finished(null, "DRAW_AGREED"));
    }

    @Test
    void whenTheHostPlaysBlackTheWhiteSeatIsTheGuest() {
        Draughts game = new Draughts(org.kevinkib.cardgames.game.GameId.generate(), BLACK);

        DraughtsDto dto = DraughtsDto.forViewer(game, BLACK);

        assertThat(dto.whiteSeat()).isEqualTo(1);
        assertThat(dto.currentSeat()).isEqualTo(1);
        assertThat(dto.players().get(0).colour()).isEqualTo("BLACK");
        assertThat(dto.players().get(1).colour()).isEqualTo("WHITE");
        assertThat(dto.legalMoves()).hasSize(9);
    }
}
