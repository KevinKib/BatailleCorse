package org.kevinkib.cardgames.bullshit.presentation.dto.event;

import org.kevinkib.cardgames.presentation.dto.event.EventData;

public record DiscardEventData(int claimantSeat, String claimedTargetLabel, int count) implements EventData {
}
