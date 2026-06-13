package org.kevinkib.cardgames.presentation.dto.event;

import org.kevinkib.cardgames.presentation.dto.PlayerIdDto;

public record RematchEventData(RematchStatus status, PlayerIdDto requestedBy) implements EventData {
}
