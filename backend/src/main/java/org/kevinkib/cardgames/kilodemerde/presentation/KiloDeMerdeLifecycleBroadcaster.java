package org.kevinkib.cardgames.kilodemerde.presentation;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.KiloDeMerdeDto;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;

/**
 * The Kilo de merde lifecycle broadcaster: the generic seat one, plus the announcement of what a forfeit caused (the
 * new deal of the re-formed table, or the end of the game), which the domain recorded.
 */
public class KiloDeMerdeLifecycleBroadcaster implements GameLifecycleBroadcaster {

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
