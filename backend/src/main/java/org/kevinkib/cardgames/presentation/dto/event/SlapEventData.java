package org.kevinkib.cardgames.presentation.dto.event;

import org.kevinkib.cardgames.bataillecorse.presentation.dto.PlayerIdDto;

public record SlapEventData(boolean isSuccessful, PlayerIdDto player) implements EventData {
}
