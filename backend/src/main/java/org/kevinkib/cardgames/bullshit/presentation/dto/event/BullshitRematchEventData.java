package org.kevinkib.cardgames.bullshit.presentation.dto.event;

import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.presentation.dto.event.RematchStatus;

public record BullshitRematchEventData(RematchStatus status, int ready, int eligible) implements EventData {
}
