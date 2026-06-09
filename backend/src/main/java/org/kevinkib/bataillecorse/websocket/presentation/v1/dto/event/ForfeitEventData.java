package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

/** Body for FORFEIT: the seat that lost (explicitly or by auto-loss). */
public record ForfeitEventData(Integer loserSeat) implements EventData {
}
