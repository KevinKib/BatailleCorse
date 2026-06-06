package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.PlayerIdDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.SeatDto;

import java.util.List;

public record JoinEventData(PlayerIdDto player, List<SeatDto> players) implements EventData {
}
