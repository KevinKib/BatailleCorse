package org.kevinkib.cardgames.presentation.dto.event;

import org.kevinkib.cardgames.bataillecorse.presentation.dto.PlayerIdDto;
import org.kevinkib.cardgames.presentation.dto.SeatDto;

import java.util.List;

public record JoinEventData(PlayerIdDto player, List<SeatDto> players) implements EventData {
}
