package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

/**
 * Body for OPPONENT_DISCONNECTED / OPPONENT_RECONNECTED.
 * @param disconnectedSeat the seat that dropped (so the recipient can ignore its own id)
 * @param deadlineEpochMs   when the auto-loss fires (epoch millis); null on reconnect
 */
public record ConnectionEventData(Integer disconnectedSeat, Long deadlineEpochMs) implements EventData {
}
