package org.kevinkib.cardgames.presentation.state;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Map;
import java.util.Set;

/** One game's view of its state for one seat: what that viewer is allowed to see. */
@FunctionalInterface
public interface SeatProjection<G extends Game, D> {

    D forViewer(G game, PlayerId viewer, Map<PlayerId, String> names, Set<PlayerId> bots);
}
