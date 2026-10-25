package org.kevinkib.cardgames.threemenmorris.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.domain.bot.MorrisBotLevel;
import org.kevinkib.cardgames.threemenmorris.presentation.bot.MorrisBotLevels;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.ThreeMenMorrisDto;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/** The computer seat and its level ride on the player entry, for every variant, so a reload keeps the name right. */
class ThreeMenMorrisBotLevelDtoTest {

    private static final PlayerId SEAT_0 = new PlayerId(0);
    private static final PlayerId SEAT_1 = new PlayerId(1);
    private static final Map<PlayerId, String> NAMES = Map.of(SEAT_0, "Alice", SEAT_1, "Bot 1");

    private static ThreeMenMorris game(MorrisRules rules) {
        return new ThreeMenMorris(GameId.generate(), rules, SEAT_0);
    }

    @Test
    void aComputerSeatIsFlaggedWithItsLevelAndAHumanSeatIsNot() {
        for (MorrisRules rules : new MorrisRules[] {MorrisRules.TAPATAN, MorrisRules.ACHI}) {
            ThreeMenMorrisDto dto = ThreeMenMorrisDto.forViewer(game(rules), SEAT_0, NAMES, Set.of(SEAT_1),
                    Map.of(SEAT_1, "perfect"));

            assertThat(dto.players().get(0).bot(), is(false));
            assertThat(dto.players().get(0).level(), is(nullValue()));
            assertThat(dto.players().get(1).bot(), is(true));
            assertThat(dto.players().get(1).level(), is("perfect"));
        }
    }

    @Test
    void theFieldsAreLeftOutOfTheJsonForAHumanSeatSoTheWireIsUnchanged() {
        String json = JsonMapper.builder().build().writeValueAsString(
                ThreeMenMorrisDto.forViewer(game(MorrisRules.TAPATAN), SEAT_0, NAMES));

        assertThat(json, not(containsString("\"bot\"")));
        assertThat(json, not(containsString("\"level\"")));
    }

    @Test
    void theLevelOfASeatIsReadFromItsSeatOptionAndFallsBackToTheDefault() {
        GameId id = GameId.generate();
        Set<PlayerId> bots = Set.of(SEAT_0, SEAT_1);

        Map<PlayerId, String> levels = MorrisBotLevels.of(id, bots,
                (gameId, seat) -> seat.equals(SEAT_1) ? Map.of("level", "perfect") : Map.of());

        assertThat(levels.get(SEAT_1), is("perfect"));
        assertThat(levels.get(SEAT_0), is(MorrisBotLevel.DEFAULT.key()));
        assertThat(MorrisBotLevels.of(id, Set.of(), (gameId, seat) -> Map.of()).isEmpty(), is(true));
    }

    @Test
    void theFieldsAreWrittenForAComputerSeat() {
        String json = JsonMapper.builder().build().writeValueAsString(
                ThreeMenMorrisDto.forViewer(game(MorrisRules.TAPATAN), SEAT_0, NAMES, Set.of(SEAT_1),
                        Map.of(SEAT_1, "random")));

        assertThat(json, containsString("\"bot\":true"));
        assertThat(json, containsString("\"level\":\"random\""));
    }
}
