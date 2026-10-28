package org.kevinkib.cardgames.blackjack.presentation;

import org.kevinkib.cardgames.blackjack.domain.Blackjack;
import org.kevinkib.cardgames.blackjack.presentation.dto.BlackjackDto;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;

/**
 * The Blackjack lifecycle broadcaster: the generic seat one, plus the announcement of what a forfeit caused (the deal
 * that no longer waits for the seat, the dealer's turn, the end of the game), which the domain recorded.
 */
public class BlackjackLifecycleBroadcaster implements GameLifecycleBroadcaster {

    private final SeatLifecycleBroadcaster<Blackjack> seats;
    private final BlackjackGameActions actions;

    public BlackjackLifecycleBroadcaster(SeatStateBroadcaster<Blackjack, BlackjackDto> broadcaster,
                                         BlackjackGameActions actions) {
        this.seats = new SeatLifecycleBroadcaster<>(Blackjack.class, broadcaster);
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
        actions.announceForfeitAftermath((Blackjack) game, "Player " + player.id() + " forfeited.");
    }
}
