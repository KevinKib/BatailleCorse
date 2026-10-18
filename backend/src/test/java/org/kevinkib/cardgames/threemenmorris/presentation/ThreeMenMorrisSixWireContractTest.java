package org.kevinkib.cardgames.threemenmorris.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisEvent;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;
import org.kevinkib.cardgames.threemenmorris.domain.SixGames;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisException;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.ThreeMenMorrisDto;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.event.ThreeMenMorrisEventData;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Characterisation of the wire contract of Six Men's Morris (Tapatan and Achi are pinned by
 * {@link ThreeMenMorrisWireContractTest} and {@link ThreeMenMorrisVariantsWireContractTest}, untouched by the
 * mill rules): the exact JSON of the per-viewer DTO and of the event data, including {@code removal},
 * {@code legalRemovals}, {@code lost}, {@code quietPlies}, {@code quietPlyLimit} and the {@code mill} of an event.
 */
class ThreeMenMorrisSixWireContractTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);
    private static final GameId ID = new GameId("00000000-0000-0000-0000-000000000001");
    private static final Map<PlayerId, String> NAMES = Map.of(WHITE, "Alice", BLACK, "Bob");
    private static final int PLACE = -1;
    private static final int REMOVE = -2;

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private String json(Object value) {
        return mapper.writeValueAsString(value);
    }

    private String views(ThreeMenMorris game) {
        return json(ThreeMenMorrisDto.forViewer(game, WHITE, NAMES)) + "\n"
                + json(ThreeMenMorrisDto.forViewer(game, BLACK));
    }

    /** Steps of three integers: seat, then PLACE or REMOVE or a source point, then the target point. */
    private void play(ThreeMenMorris game, List<String> trace, int... steps) throws ThreeMenMorrisException {
        for (int i = 0; i < steps.length; i += 3) {
            PlayerId seat = new PlayerId(steps[i]);
            MorrisEvent event = switch (steps[i + 1]) {
                case PLACE -> game.place(seat, steps[i + 2]);
                case REMOVE -> game.remove(seat, steps[i + 2]);
                default -> game.move(seat, steps[i + 1], steps[i + 2]);
            };
            trace.add(json(ThreeMenMorrisEventData.from(event)));
        }
    }

    @Test
    void theJsonOfSixViewsAndEventsIsPinned() throws Exception {
        List<String> out = new ArrayList<>();

        ThreeMenMorris six = new ThreeMenMorris(ID, MorrisRules.SIX_MEN, WHITE);
        out.add("SIX-NEW\n" + views(six));
        play(six, out, 0, PLACE, 0, 1, PLACE, 8, 0, PLACE, 1, 1, PLACE, 10, 0, PLACE, 2);
        out.add("SIX-MILL-PENDING\n" + views(six));
        play(six, out, 0, REMOVE, 8);
        out.add("SIX-AFTER-REMOVAL\n" + views(six));

        ThreeMenMorris movement = SixGames.six(ID, SixGames.points(0, 1, 3, 9, 12, 15),
                SixGames.points(4, 5, 6, 10, 11, 13), WHITE);
        out.add("SIX-MOVEMENT\n" + views(movement));
        play(movement, out, 0, 3, 2);
        out.add("SIX-MILL-BY-MOVE\n" + views(movement));
        play(movement, out, 0, REMOVE, 10);

        ThreeMenMorris reduced = SixGames.game(ID, MorrisRules.SIX_MEN, SixGames.points(0, 1), SixGames.points(8, 12),
                WHITE, 6, 3);
        play(reduced, out, 0, PLACE, 2, 0, REMOVE, 8);
        out.add("SIX-REDUCED\n" + views(reduced));

        ThreeMenMorris quiet = SixGames.twoLonePieces(ID, MorrisRules.SIX_MEN);
        SixGames.Walker walker = new SixGames.Walker(quiet);
        walker.play(40);
        out.add("SIX-QUIET-WARNING\n" + views(quiet));
        walker.play(10);
        out.add("SIX-DRAW-MOVE-LIMIT\n" + views(quiet));

        assertThat(String.join("\n", out), is(golden()));
    }

    private static String golden() throws IOException {
        try (var in = ThreeMenMorrisSixWireContractTest.class.getResourceAsStream(
                "/golden/three-men-morris-six-wire.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").stripTrailing();
        }
    }
}
