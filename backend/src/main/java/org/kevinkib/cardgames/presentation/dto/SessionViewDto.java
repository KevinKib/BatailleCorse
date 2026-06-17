package org.kevinkib.cardgames.presentation.dto;

import org.kevinkib.cardgames.sessionmanagement.core.application.SeatView;

import java.util.List;

public record SessionViewDto(List<SeatDto> players) {

    public static SessionViewDto from(List<SeatView> seats) {
        List<SeatDto> dtos = seats.stream()
                .map(seat -> new SeatDto(seat.seat(), seat.name(), seat.joined()))
                .toList();
        return new SessionViewDto(dtos);
    }
}
