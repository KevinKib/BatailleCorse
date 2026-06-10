package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

public record OpponentReconnectedEventData(int reconnectedSeat) implements EventData {
}
