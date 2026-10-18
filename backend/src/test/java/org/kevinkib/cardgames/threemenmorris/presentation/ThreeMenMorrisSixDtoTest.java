package org.kevinkib.cardgames.threemenmorris.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;
import org.kevinkib.cardgames.threemenmorris.domain.SixGames;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.ThreeMenMorrisDto;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/** The additive fields of the view for the mill variants: removal, legalRemovals, lost, quietPlies. */
class ThreeMenMorrisSixDtoTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);

    private static String json(ThreeMenMorrisDto dto) {
        return JsonMapper.builder().build().writeValueAsString(dto);
    }

    private ThreeMenMorris pendingRemoval() throws Exception {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate(), MorrisRules.SIX_MEN, WHITE);
        int[] placements = {0, 8, 1, 10, 2};
        for (int i = 0; i < placements.length; i++) {
            game.place(i % 2 == 0 ? WHITE : BLACK, placements[i]);
        }
        return game;
    }

    @Test
    void aFreshSixGameShowsSixteenPointsItsVariantTheLostCountsAndTheQuietLimit() {
        ThreeMenMorris game = new ThreeMenMorris(GameId.generate(), MorrisRules.SIX_MEN, WHITE);

        ThreeMenMorrisDto dto = ThreeMenMorrisDto.forViewer(game, WHITE);

        assertThat(dto.variant(), is("six"));
        assertThat(dto.board().size(), is(16));
        assertThat(dto.players().get(0).piecesToPlace(), is(6));
        assertThat(dto.players().get(0).lost(), is(0));
        assertThat(dto.players().get(1).lost(), is(0));
        assertThat(dto.legalPlacements().size(), is(16));
        assertThat(dto.quietPlyLimit(), is(50));
        assertThat(dto.quietPlies(), nullValue());
        assertThat(dto.removal(), nullValue());
        assertThat(dto.legalRemovals(), nullValue());
        assertThat(dto.winningLine(), is(List.of()));
    }

    @Test
    void aPendingRemovalIsShownToBothSeatsAndItsLegalPointsOnlyToTheRemover() throws Exception {
        ThreeMenMorris game = pendingRemoval();

        ThreeMenMorrisDto white = ThreeMenMorrisDto.forViewer(game, WHITE);
        ThreeMenMorrisDto black = ThreeMenMorrisDto.forViewer(game, BLACK);

        assertThat(white.removal(), is(new ThreeMenMorrisDto.RemovalDto(0, List.of(0, 1, 2))));
        assertThat(black.removal(), is(new ThreeMenMorrisDto.RemovalDto(0, List.of(0, 1, 2))));
        assertThat(white.availableActions(), is(List.of("REMOVE")));
        assertThat(black.availableActions(), is(List.of()));
        assertThat(white.legalRemovals(), is(List.of(8, 10)));
        assertThat(black.legalRemovals(), nullValue());
        assertThat(white.legalPlacements(), is(List.of()));
        assertThat(white.legalMoves(), is(List.of()));
        assertThat(json(black), not(containsString("legalRemovals")));
    }

    @Test
    void afterTheRemovalTheMillIsGoneAndTheLostCountGrows() throws Exception {
        ThreeMenMorris game = pendingRemoval();
        game.remove(WHITE, 8);

        ThreeMenMorrisDto dto = ThreeMenMorrisDto.forViewer(game, BLACK);

        assertThat(dto.removal(), nullValue());
        assertThat(dto.legalRemovals(), nullValue());
        assertThat(dto.players().get(1).lost(), is(1));
        assertThat(dto.players().get(0).lost(), is(0));
        assertThat(dto.board().get(8), nullValue());
        assertThat(dto.currentSeat(), is(1));
    }

    @Test
    void aReducedGameIsFinishedWithItsReason() throws Exception {
        ThreeMenMorris game = SixGames.game(MorrisRules.SIX_MEN, SixGames.points(0, 1), SixGames.points(8, 12),
                WHITE, 6, 3);
        game.place(WHITE, 2);
        game.remove(WHITE, 8);

        ThreeMenMorrisDto dto = ThreeMenMorrisDto.forViewer(game, WHITE);

        assertThat(dto.outcome(), is(new ThreeMenMorrisDto.Finished(0, "REDUCED")));
        assertThat(dto.players().get(1).lost(), is(4));
        assertThat(dto.removal(), nullValue());
        assertThat(dto.availableActions(), is(List.of()));
    }

    @Test
    void theQuietPlyCountIsWrittenFromFortyAndNotBefore() throws Exception {
        ThreeMenMorris game = SixGames.twoLonePieces(MorrisRules.SIX_MEN);

        SixGames.Walker walker = new SixGames.Walker(game);
        walker.play(39);
        assertThat(ThreeMenMorrisDto.forViewer(game, WHITE).quietPlies(), nullValue());

        walker.play(1);
        assertThat(ThreeMenMorrisDto.forViewer(game, WHITE).quietPlies(), is(40));
        assertThat(json(ThreeMenMorrisDto.forViewer(game, WHITE)), containsString("\"quietPlies\":40"));
    }

    @Test
    void aDrawnGameCarriesItsReason() throws Exception {
        ThreeMenMorris game = SixGames.twoLonePieces(MorrisRules.SIX_MEN);
        new SixGames.Walker(game).play(50);

        assertThat(ThreeMenMorrisDto.forViewer(game, WHITE).outcome(),
                is(new ThreeMenMorrisDto.Finished(null, "DRAW_MOVE_LIMIT")));
    }

    @Test
    void sixSerialisesTheNewFieldsAndTheOtherVariantsNone() throws Exception {
        String six = json(ThreeMenMorrisDto.forViewer(pendingRemoval(), WHITE));
        assertThat(six, containsString("\"removal\":{\"seat\":0,\"mill\":[0,1,2]}"));
        assertThat(six, containsString("\"legalRemovals\":[8,10]"));
        assertThat(six, containsString("\"lost\":0"));
        assertThat(six, containsString("\"quietPlyLimit\":50"));

        for (MorrisRules rules : List.of(MorrisRules.TAPATAN, MorrisRules.ACHI)) {
            String other = json(ThreeMenMorrisDto.forViewer(new ThreeMenMorris(GameId.generate(), rules, WHITE), WHITE));
            for (String field : List.of("removal", "legalRemovals", "lost", "quietPlies", "quietPlyLimit")) {
                assertThat(other, not(containsString(field)));
            }
        }
    }
}
