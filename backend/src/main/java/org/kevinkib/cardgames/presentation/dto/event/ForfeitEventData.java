package org.kevinkib.cardgames.presentation.dto.event;

/** @param reason why the seat left (RESIGNED or DISCONNECTED); null when not known */
public record ForfeitEventData(Integer loserSeat, String reason) implements EventData {

    public ForfeitEventData(Integer loserSeat) {
        this(loserSeat, null);
    }
}
