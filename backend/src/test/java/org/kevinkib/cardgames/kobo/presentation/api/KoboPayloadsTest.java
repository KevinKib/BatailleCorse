package org.kevinkib.cardgames.kobo.presentation.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/** With a lenient mapper (null into a primitive allowed), a field the client forgot must stay absent (null), never silently become seat 0 or slot 0. */
class KoboPayloadsTest {

    private final ObjectMapper mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    private static final String HEAD = "{\"gameId\":\"g\",\"token\":\"t\"";

    @Test
    void missingFieldsAreNull() throws Exception {
        KoboGiftPayload gift = mapper.readValue(HEAD + "}", KoboGiftPayload.class);
        KoboTargetPayload target = mapper.readValue(HEAD + "}", KoboTargetPayload.class);
        KoboSwapPayload swap = mapper.readValue(HEAD + "}", KoboSwapPayload.class);
        KoboMatchPayload match = mapper.readValue(HEAD + "}", KoboMatchPayload.class);
        KoboSlotPayload slot = mapper.readValue(HEAD + "}", KoboSlotPayload.class);

        assertThat(gift.seat(), is(nullValue()));
        assertThat(target.seat(), is(nullValue()));
        assertThat(target.slot(), is(nullValue()));
        assertThat(swap.ownSlot(), is(nullValue()));
        assertThat(swap.seat(), is(nullValue()));
        assertThat(swap.slot(), is(nullValue()));
        assertThat(match.seat(), is(nullValue()));
        assertThat(match.slot(), is(nullValue()));
        assertThat(slot.slot(), is(nullValue()));
    }

    @Test
    void presentFieldsAreRead() throws Exception {
        KoboGiftPayload gift = mapper.readValue(HEAD + ",\"seat\":2}", KoboGiftPayload.class);

        assertThat(gift.seat(), is(2));
    }
}
