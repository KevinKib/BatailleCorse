package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

public record OpponentDisconnectedEventData(int disconnectedSeat, long deadlineEpochMs) implements EventData {
}
