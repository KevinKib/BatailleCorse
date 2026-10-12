package org.kevinkib.cardgames.presentation.dto;

import org.kevinkib.cardgames.sessionmanagement.core.application.SeatView;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public record SessionViewDto(List<SeatDto> players) {

    public static SessionViewDto from(List<SeatView> seats) {
        return from(seats, seat -> Map.of());
    }

    /** {@code seatOptions} gives the options of a seat (only asked for a bot seat). */
    public static SessionViewDto from(List<SeatView> seats, Function<Integer, Map<String, String>> seatOptions) {
        List<SeatDto> dtos = seats.stream()
                .map(seat -> new SeatDto(seat.seat(), seat.name(), seat.joined(), seat.bot(),
                        seat.bot() ? seatOptions.apply(seat.seat()) : Map.of()))
                .toList();
        return new SessionViewDto(dtos);
    }
}
