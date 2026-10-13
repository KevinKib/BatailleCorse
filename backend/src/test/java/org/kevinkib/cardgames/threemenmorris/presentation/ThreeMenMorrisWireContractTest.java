package org.kevinkib.cardgames.threemenmorris.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisEvent;
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
 * Characterisation of the wire contract: the exact JSON of the per-viewer DTO and of the event data in
 * the situations of a game (new, placement, movement, line, blocked, forfeit). The refactoring of the
 * rules into data must keep every byte of it.
 */
class ThreeMenMorrisWireContractTest {

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
        for (int i = 0; i < steps.length; ) {
            PlayerId seat = new PlayerId(steps[i]);
            MorrisEvent event;
            if (steps[i + 1] == -1) {
                event = game.place(seat, steps[i + 2]);
                i += 3;
            } else {
                event = game.move(seat, steps[i + 1], steps[i + 2]);
                i += 3;
            }
            trace.add(json(ThreeMenMorrisEventData.from(event)));
        }
    }

    @Test
    void theJsonOfEveryViewAndEventIsUnchanged() throws Exception {
        List<String> out = new ArrayList<>();
        ThreeMenMorris game = new ThreeMenMorris(ID);
        out.add("NEW\n" + views(game));
        play(game, out, 0, -1, 0, 1, -1, 1);
        out.add("PLACEMENT\n" + views(game));
        play(game, out, 0, -1, 2, 1, -1, 4, 0, -1, 7, 1, -1, 8);
        out.add("MOVEMENT\n" + views(game));
        play(game, out, 0, 0, 3);
        out.add("AFTER-MOVE\n" + views(game));

        ThreeMenMorris line = new ThreeMenMorris(ID);
        play(line, out, 0, -1, 0, 1, -1, 3, 0, -1, 4, 1, -1, 1, 0, -1, 8);
        out.add("LINE\n" + views(line));

        ThreeMenMorris forfeit = new ThreeMenMorris(ID);
        forfeit.forfeit(BLACK);
        out.add("FORFEIT\n" + views(forfeit));
        assertThat(String.join("\n", out), is(golden()));
    }

    private static String golden() throws IOException {
        try (var in = ThreeMenMorrisWireContractTest.class.getResourceAsStream(
                "/golden/three-men-morris-wire.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").stripTrailing();
        }
    }
}
