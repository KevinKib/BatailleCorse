package org.kevinkib.cardgames.presentation.dto.event;

import org.kevinkib.cardgames.presentation.dto.PlayerIdDto;

public record SendEventData(PlayerIdDto player) implements EventData {
}
