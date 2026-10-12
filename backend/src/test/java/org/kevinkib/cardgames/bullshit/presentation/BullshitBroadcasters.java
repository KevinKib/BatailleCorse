package org.kevinkib.cardgames.bullshit.presentation;

import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.presentation.dto.BullshitDto;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.SeatNames;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;

/** Test wiring of the Bullshit broadcasters, as {@code BullshitConfig} builds them. */
public final class BullshitBroadcasters {

    private BullshitBroadcasters() {
    }

    public static SeatStateBroadcaster<Bullshit, BullshitDto> state(GameMessagingService messaging) {
        return state(messaging, BotSeats.none());
    }

    public static SeatStateBroadcaster<Bullshit, BullshitDto> state(GameMessagingService messaging,
                                                                    BotSeats botSeats) {
        return state(messaging, botSeats, SeatNames.none());
    }

    public static SeatStateBroadcaster<Bullshit, BullshitDto> state(GameMessagingService messaging,
                                                                    BotSeats botSeats, SeatNames seatNames) {
        return new SeatStateBroadcaster<>(messaging, botSeats, seatNames, BullshitDto::forViewer);
    }

    public static SeatLifecycleBroadcaster<Bullshit> lifecycle(SeatStateBroadcaster<Bullshit, BullshitDto> state) {
        return new SeatLifecycleBroadcaster<>(Bullshit.class, state);
    }
}
