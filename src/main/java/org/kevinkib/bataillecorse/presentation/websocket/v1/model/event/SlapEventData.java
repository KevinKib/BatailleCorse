package org.kevinkib.bataillecorse.presentation.websocket.v1.model.event;

import org.kevinkib.bataillecorse.presentation.websocket.v1.model.PlayerIdDto;

public record SlapEventData(boolean isSuccessful, PlayerIdDto player) implements EventData {
}
