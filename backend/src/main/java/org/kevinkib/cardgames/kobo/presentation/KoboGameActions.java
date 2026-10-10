package org.kevinkib.cardgames.kobo.presentation;

import org.kevinkib.cardgames.kobo.domain.event.KoboEvent;
import org.kevinkib.cardgames.kobo.domain.rules.KoboException;
import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.kevinkib.cardgames.kobo.domain.tableau.SlotRef;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.presentation.dto.event.KoboEventData;
import org.kevinkib.cardgames.kobo.presentation.dto.event.KoboEventType;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cards.domain.Rank;

/**
 * The Kobo use cases: domain call, session touch, then one broadcast. Authentication stays with the
 * caller; domain exceptions propagate to it. The call and the broadcast run under the monitor of
 * the game, so two seats events leave in the order the state changed.
 */
public class KoboGameActions {

    @FunctionalInterface
    private interface Command {
        KoboEvent apply(Kobo game) throws KoboException;
    }

    private final SessionService sessionService;
    private final KoboStateBroadcaster broadcaster;

    public KoboGameActions(SessionService sessionService, KoboStateBroadcaster broadcaster) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
    }

    public void ready(GameId id, PlayerId seat) throws KoboException {
        run(id, g -> g.ready(seat), "Player " + seat + " is ready.");
    }

    public void draw(GameId id, PlayerId seat) throws KoboException {
        run(id, g -> g.draw(seat), "Player " + seat + " drew a card.");
    }

    public void swapDrawn(GameId id, PlayerId seat, int slot) throws KoboException {
        run(id, g -> g.swapDrawn(seat, slot), "Player " + seat + " swapped the drawn card.");
    }

    public void discardDrawn(GameId id, PlayerId seat) throws KoboException {
        run(id, g -> g.discardDrawn(seat), "Player " + seat + " discarded the drawn card.");
    }

    public void skipPower(GameId id, PlayerId seat) throws KoboException {
        run(id, g -> g.skipPower(seat), "Player " + seat + " skipped the power.");
    }

    public void peek(GameId id, PlayerId seat, SlotRef target) throws KoboException {
        run(id, g -> g.peek(seat, target), "Player " + seat + " looked at a card.");
    }

    public void blindSwap(GameId id, PlayerId seat, int ownSlot, SlotRef other) throws KoboException {
        run(id, g -> g.blindSwap(seat, ownSlot, other), "Player " + seat + " swapped two cards.");
    }

    public void kingSwap(GameId id, PlayerId seat, int ownSlot) throws KoboException {
        run(id, g -> g.kingSwap(seat, ownSlot), "Player " + seat + " swapped the card they looked at.");
    }

    public void endTurn(GameId id, PlayerId seat) throws KoboException {
        run(id, g -> g.endTurn(seat), "Player " + seat + " ended their turn.");
    }

    public void announceKobo(GameId id, PlayerId seat) throws KoboException {
        run(id, g -> g.announceKobo(seat), "Player " + seat + " announced Kobo.");
    }

    public void match(GameId id, PlayerId seat, SlotRef target, Integer giveSlot, Rank expectedTopRank,
                      Integer expectedRevision) throws KoboException {
        run(id, g -> g.matchDiscard(seat, target, giveSlot, expectedTopRank, expectedRevision),
                "Player " + seat + " tried to discard a card.");
    }

    public void giveTen(GameId id, PlayerId seat, PlayerId target) throws KoboException {
        run(id, g -> g.giveTen(seat, target), "Player " + seat + " gave 10 points to player " + target + ".");
    }

    private void run(GameId gameId, Command command, String message) throws KoboException {
        Kobo game = sessionService.getGame(gameId, Kobo.class);
        synchronized (game) {
            KoboEvent event = command.apply(game);
            sessionService.touch(gameId);
            broadcaster.broadcast(game, broadcastType(game, event).toString(), KoboEventData.from(event), message);
        }
    }

    /** A command that closes a round or the game is announced as such; the data still names the command. */
    private static KoboEventType broadcastType(Kobo game, KoboEvent event) {
        if (game.isFinished()) {
            return KoboEventType.GAME_OVER;
        }
        if (game.phase() == Phase.ROUND_OVER) {
            return KoboEventType.ROUND_END;
        }
        return KoboEventData.typeOf(event);
    }
}
