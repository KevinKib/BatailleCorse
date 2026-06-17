package org.kevinkib.cardgames.presentation.dto;

import org.kevinkib.cardgames.sessionmanagement.session.domain.SessionPlayer;

import java.util.List;

public record SessionViewDto(List<SeatDto> players) {

    public static SessionViewDto from(List<SessionPlayer> seats) {
        List<SeatDto> dtos = seats.stream()
                .map(seat -> new SeatDto(seat.id().id(), seat.name(), seat.isClaimed()))
                .toList();
        return new SessionViewDto(dtos);
    }
}
