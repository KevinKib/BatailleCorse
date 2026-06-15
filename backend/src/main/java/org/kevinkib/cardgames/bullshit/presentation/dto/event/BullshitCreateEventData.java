package org.kevinkib.cardgames.bullshit.presentation.dto.event;

import org.kevinkib.cardgames.presentation.dto.event.EventData;

import java.util.Map;

public record BullshitCreateEventData(String gameId, String gameType, Map<Integer, String> tokens) implements EventData {
}
