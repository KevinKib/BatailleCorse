package org.kevinkib.cardgames.presentation.dto;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.sessionmanagement.core.application.SeatView;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class SessionViewDtoTest {

    @Test
    void givenSeatsWithoutAName_whenFrom_thenTheNameStaysNull() {
        SessionViewDto dto = SessionViewDto.from(List.of(
                new SeatView(0, null, true, false),
                new SeatView(1, null, true, true),
                new SeatView(2, "Carol", true, false)),
                seat -> Map.of("tier", "1"));

        assertThat(dto.players().get(0).name(), is(nullValue()));
        assertThat(dto.players().get(1).name(), is(nullValue()));
        assertThat(dto.players().get(1).bot(), is(true));
        assertThat(dto.players().get(2).name(), is("Carol"));
    }
}
