package org.kevinkib.cardgames.bullshit.presentation.dto.event;

import org.kevinkib.cardgames.bullshit.presentation.dto.CardDto;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

import java.util.List;

public record CallBullshitEventData(
        int callerSeat,
        int claimantSeat,
        boolean truthful,
        int pickerSeat,
        List<CardDto> revealedCards) implements EventData {
}
