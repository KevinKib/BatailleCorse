package org.kevinkib.cardgames.mangola.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.bot.MangolaBotLevel;
import org.kevinkib.cardgames.mangola.presentation.bot.MangolaBotLevels;
import org.kevinkib.cardgames.mangola.presentation.dto.MangolaDto;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/** The computer seat and its level ride on the player entry, so a reload mid-game keeps the name right. */
class MangolaBotLevelDtoTest {

    private static final PlayerId SEAT_0 = MangolaTable.SEAT_0;
    private static final PlayerId SEAT_1 = MangolaTable.SEAT_1;
    private static final Map<PlayerId, String> NAMES = Map.of(SEAT_0, "Alice", SEAT_1, "Bot 1");

    private static Mangola game() {
        return new Mangola(GameId.generate(), MangolaRules.OWNER, SEAT_0);
    }

    @Test
    void aComputerSeatIsFlaggedWithItsLevelAndAHumanSeatIsNot() {
        MangolaDto dto = MangolaDto.forViewer(game(), SEAT_0, NAMES, Set.of(SEAT_1), Map.of(SEAT_1, "expert"));

        assertThat(dto.players().get(0).bot(), is(false));
        assertThat(dto.players().get(0).level(), is(nullValue()));
        assertThat(dto.players().get(1).bot(), is(true));
        assertThat(dto.players().get(1).level(), is("expert"));
    }

    @Test
    void aComputerSeatWithoutAKnownLevelIsStillFlagged() {
        MangolaDto dto = MangolaDto.forViewer(game(), SEAT_0, NAMES, Set.of(SEAT_1), Map.of());

        assertThat(dto.players().get(1).bot(), is(true));
        assertThat(dto.players().get(1).level(), is(nullValue()));
    }

    @Test
    void theFieldsAreLeftOutOfTheJsonForAHumanSeatSoTheWireIsUnchanged() {
        String json = JsonMapper.builder().build().writeValueAsString(MangolaDto.forViewer(game(), SEAT_0, NAMES));

        assertThat(json, not(containsString("\"bot\"")));
        assertThat(json, not(containsString("\"level\"")));
    }

    @Test
    void theLevelOfASeatIsReadFromItsSeatOptionAndFallsBackToTheDefault() {
        GameId id = GameId.generate();
        Set<PlayerId> bots = Set.of(SEAT_0, SEAT_1);

        Map<PlayerId, String> levels = MangolaBotLevels.of(id, bots,
                (gameId, seat) -> seat.equals(SEAT_1) ? Map.of("level", "expert") : Map.of());

        assertThat(levels.get(SEAT_1), is("expert"));
        assertThat(levels.get(SEAT_0), is(MangolaBotLevel.DEFAULT.key()));
        assertThat(MangolaBotLevels.of(id, Set.of(), (gameId, seat) -> Map.of()).isEmpty(), is(true));
    }

    @Test
    void theFieldsAreWrittenForAComputerSeat() {
        String json = JsonMapper.builder().build().writeValueAsString(
                MangolaDto.forViewer(game(), SEAT_0, NAMES, Set.of(SEAT_1), Map.of(SEAT_1, "random")));

        assertThat(json, containsString("\"bot\":true"));
        assertThat(json, containsString("\"level\":\"random\""));
    }
}
