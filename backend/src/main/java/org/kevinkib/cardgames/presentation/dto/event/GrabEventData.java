package org.kevinkib.cardgames.presentation.dto.event;

import org.kevinkib.cardgames.presentation.dto.PlayerIdDto;

public record GrabEventData(PlayerIdDto player) implements EventData {
}
