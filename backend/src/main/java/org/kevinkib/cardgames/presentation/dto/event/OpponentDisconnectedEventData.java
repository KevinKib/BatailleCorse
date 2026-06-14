package org.kevinkib.cardgames.presentation.dto.event;

public record OpponentDisconnectedEventData(int disconnectedSeat, long deadlineEpochMs) implements EventData {
}
