package org.kevinkib.cardgames.bataillecorse.presentation.dto.event;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

import org.kevinkib.cardgames.bataillecorse.presentation.dto.PlayerIdDto;

public record GrabEventData(PlayerIdDto player) implements EventData {
}
