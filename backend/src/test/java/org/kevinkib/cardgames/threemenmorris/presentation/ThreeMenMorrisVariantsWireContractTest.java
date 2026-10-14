package org.kevinkib.cardgames.threemenmorris.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisEvent;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;
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
 * Characterisation of the wire contract of Achi and Picaria (the Tapatan one is pinned by
 * {@link ThreeMenMorrisWireContractTest}, which M2 leaves untouched): the exact JSON of the per-viewer
 * DTO and of the event data, including the additive fields {@code variant} and {@code legalPlacements}.
 */
class ThreeMenMorrisVariantsWireContractTest {

    private static final PlayerId WHITE = new PlayerId(0);
    private static final PlayerId BLACK = new PlayerId(1);
    private static final GameId ID = new GameId("00000000-0000-0000-0000-000000000001");
    private static final Map<PlayerId, String> NAMES = Map.of(WHITE, "Alice", BLACK, "Bob");

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private String json(Object value) {
        return mapper.writeValueAsString(value);
    }

    private String views(ThreeMenMorris game) {
        return json(ThreeMenMorrisDto.forViewer(game, WHITE, NAMES)) + "\n"
                + json(ThreeMenMorrisDto.forViewer(game, BLACK));
    }

    private void play(ThreeMenMorris game, List<String> trace, int... steps) throws ThreeMenMorrisException {
        for (int i = 0; i < steps.length; i += 3) {
            PlayerId seat = new PlayerId(steps[i]);
            MorrisEvent event = steps[i + 1] == -1
                    ? game.place(seat, steps[i + 2])
                    : game.move(seat, steps[i + 1], steps[i + 2]);
            trace.add(json(ThreeMenMorrisEventData.from(event)));
        }
    }

    @Test
    void theJsonOfAchiAndPicariaViewsAndEventsIsPinned() throws Exception {
        List<String> out = new ArrayList<>();

        ThreeMenMorris achi = new ThreeMenMorris(ID, MorrisRules.ACHI, WHITE);
        out.add("ACHI-NEW\n" + views(achi));
        play(achi, out, 0, -1, 0, 1, -1, 1, 0, -1, 2, 1, -1, 4, 0, -1, 7, 1, -1, 5, 0, -1, 3, 1, -1, 6);
        out.add("ACHI-MOVEMENT\n" + views(achi));
        play(achi, out, 0, 7, 8);
        out.add("ACHI-AFTER-MOVE\n" + views(achi));

        ThreeMenMorris blocked = new ThreeMenMorris(ID, MorrisRules.ACHI, WHITE);
        play(blocked, out, 0, -1, 2, 1, -1, 1, 0, -1, 5, 1, -1, 3, 0, -1, 6, 1, -1, 4, 0, -1, 7, 1, -1, 8);
        out.add("ACHI-BLOCKED\n" + views(blocked));

        ThreeMenMorris picaria = new ThreeMenMorris(ID, MorrisRules.PICARIA, WHITE);
        out.add("PICARIA-NEW\n" + views(picaria));
        play(picaria, out, 0, -1, 1);
        out.add("PICARIA-WHITE-PLACED\n" + views(picaria));
        play(picaria, out, 1, -1, 0);
        out.add("PICARIA-CENTRE-STILL-CLOSED\n" + views(picaria));
        play(picaria, out, 0, -1, 6, 1, -1, 2, 0, -1, 8, 1, -1, 5);
        out.add("PICARIA-MOVEMENT\n" + views(picaria));
        play(picaria, out, 0, 1, 3);
        out.add("PICARIA-AFTER-SHORT-DIAGONAL\n" + views(picaria));
        play(picaria, out, 1, 5, 4);
        out.add("PICARIA-BLACK-MOVE-TO-CENTRE\n" + views(picaria));

        assertThat(String.join("\n", out), is(golden()));
    }

    private static String golden() throws IOException {
        try (var in = ThreeMenMorrisVariantsWireContractTest.class.getResourceAsStream(
                "/golden/three-men-morris-variants-wire.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").stripTrailing();
        }
    }
}
