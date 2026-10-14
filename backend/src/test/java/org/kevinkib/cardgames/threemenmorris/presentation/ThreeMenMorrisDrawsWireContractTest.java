package org.kevinkib.cardgames.threemenmorris.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisEvent;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
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
 * Characterisation of the draw additions to the wire: the exact JSON of the per-viewer DTO and of the
 * event data in a pending offer, after a decline, with a repeated position, and for both kinds of draw.
 * The Tapatan and variants contracts ({@link ThreeMenMorrisWireContractTest},
 * {@link ThreeMenMorrisVariantsWireContractTest}) are untouched and prove that a game without a draw
 * situation serialises exactly as before.
 */
class ThreeMenMorrisDrawsWireContractTest {

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

    private ThreeMenMorris movement() throws Exception {
        ThreeMenMorris game = new ThreeMenMorris(ID);
        for (int[] p : new int[][]{{0, 0}, {1, 1}, {0, 2}, {1, 5}, {0, 7}, {1, 8}}) {
            game.place(new PlayerId(p[0]), p[1]);
        }
        return game;
    }

    private void event(List<String> out, MorrisEvent event) {
        out.add(json(ThreeMenMorrisEventData.from(event)));
    }

    @Test
    void theJsonOfTheDrawSituationsIsPinned() throws Exception {
        List<String> out = new ArrayList<>();

        ThreeMenMorris offered = movement();
        event(out, offered.offerDraw(BLACK));
        out.add("OFFER-PENDING\n" + views(offered));
        event(out, offered.declineDraw(WHITE));
        out.add("OFFER-DECLINED\n" + views(offered));
        event(out, offered.move(WHITE, 7, 6));
        event(out, offered.offerDraw(WHITE));
        event(out, offered.acceptDraw(BLACK));
        out.add("DRAW-AGREED\n" + views(offered));

        ThreeMenMorris repeated = movement();
        for (int i = 0; i < 2; i++) {
            repeated.move(WHITE, 7, 6);
            repeated.move(BLACK, 8, 4);
            repeated.move(WHITE, 6, 7);
            if (i == 0) {
                event(out, repeated.move(BLACK, 4, 8));
                out.add("REPETITION-WARNING\n" + views(repeated));
            }
        }
        event(out, repeated.move(BLACK, 4, 8));
        out.add("DRAW-REPETITION\n" + views(repeated));

        assertThat(String.join("\n", out), is(golden()));
    }

    private static String golden() throws IOException {
        try (var in = ThreeMenMorrisDrawsWireContractTest.class.getResourceAsStream(
                "/golden/three-men-morris-draws-wire.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").stripTrailing();
        }
    }
}
