package org.kevinkib.cardgames.bataillecorse.presentation.dto.event;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

import java.util.Map;

import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseIdDto;

public record CreateEventData(BatailleCorseIdDto game, Map<Integer, String> tokens) implements EventData {
}
