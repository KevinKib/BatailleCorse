package org.kevinkib.cardgames.kilodemerde.presentation;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.KiloDeMerdeDto;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Kilo de merde lifecycle broadcaster: the generic seat one, plus the announcement of what a forfeit caused (the
 * new deal of the re-formed table, or the end of the game), which the domain recorded.
 */
public class KiloDeMerdeLifecycleBroadcaster implements GameLifecycleBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(KiloDeMerdeLifecycleBroadcaster.class);

    private final SeatLifecycleBroadcaster<KiloDeMerde> seats;
    private final KiloDeMerdeGameActions actions;

    public KiloDeMerdeLifecycleBroadcaster(SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> broadcaster,
                                           KiloDeMerdeGameActions actions) {
        this.seats = new SeatLifecycleBroadcaster<>(KiloDeMerde.class, broadcaster);
        this.actions = actions;
    }

    @Override
    public boolean supports(Game game) {
        return seats.supports(game);
    }

    @Override
    public void disconnected(Game game, PlayerId player, long deadlineEpochMs) {
        seats.disconnected(game, player, deadlineEpochMs);
        // A seat that drops while the table waits for confirmations must not hold the others up.
        try {
            actions.readyIfWaiting((KiloDeMerde) game, player);
        } catch (Exception e) {
            log.warn("Seat {} could not be counted ready after its disconnection", player, e);
        }
    }

    @Override
    public void reconnected(Game game, PlayerId player) {
        seats.reconnected(game, player);
    }

    @Override
    public void forfeited(Game game, PlayerId player, ForfeitReason reason) {
        seats.forfeited(game, player, reason);
        actions.announceForfeitAftermath((KiloDeMerde) game, "Player " + player.id() + " forfeited.");
    }
}
