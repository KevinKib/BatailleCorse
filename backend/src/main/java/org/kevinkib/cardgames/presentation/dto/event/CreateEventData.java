package org.kevinkib.cardgames.presentation.dto.event;

import java.util.Map;

import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseIdDto;

public record CreateEventData(BatailleCorseIdDto game, Map<Integer, String> tokens) implements EventData {
}
