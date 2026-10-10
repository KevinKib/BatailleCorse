package org.kevinkib.cardgames.kobo.presentation.dto.event;

import org.kevinkib.cardgames.presentation.dto.event.EventData;

import java.util.Map;

public record KoboCreateEventData(String gameId, String gameType, Map<Integer, String> tokens) implements EventData {
}
