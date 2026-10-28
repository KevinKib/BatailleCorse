package org.kevinkib.cardgames.ninetynine.presentation;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.presentation.dto.NinetyNineDto;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;

/**
 * The 99 lifecycle broadcaster: the generic seat one, plus the announcement of what a forfeit caused (the
 * elimination, a bust of the next seat, the end of the game), which the domain recorded.
 */
public class NinetyNineLifecycleBroadcaster implements GameLifecycleBroadcaster {

    private final SeatLifecycleBroadcaster<NinetyNine> seats;
    private final NinetyNineGameActions actions;

    public NinetyNineLifecycleBroadcaster(SeatStateBroadcaster<NinetyNine, NinetyNineDto> broadcaster,
                                          NinetyNineGameActions actions) {
        this.seats = new SeatLifecycleBroadcaster<>(NinetyNine.class, broadcaster);
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
        actions.announceForfeitAftermath((NinetyNine) game, "Player " + player.id() + " forfeited.");
    }
}
