package org.kevinkib.cardgames.bataillecorse.presentation;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.CannotGrabException;
import org.kevinkib.cardgames.bataillecorse.domain.CannotSlapIfNoCardsInPileException;
import org.kevinkib.cardgames.bataillecorse.domain.FinishedGameException;
import org.kevinkib.cardgames.bataillecorse.domain.FullCentralPileException;
import org.kevinkib.cardgames.bataillecorse.domain.NotPlayersTurnException;
import org.kevinkib.cardgames.bataillecorse.domain.Player;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.PlayerIdDto;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.event.BatailleCorseEventType;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.event.GrabEventData;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.event.SendEventData;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.event.SlapEventData;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;

/**
 * The send, slap and grab use cases, shared by the WebSocket controller (a human acting through a token)
 * and the bot coordinator (a bot acting for its seat): domain call, session touch, then one broadcast.
 * Authentication stays with the caller; domain exceptions propagate to it and nothing is broadcast.
 */
public class BatailleCorseGameActions {

    private final SessionService sessionService;
    private final BatailleCorseStateBroadcaster broadcaster;

    public BatailleCorseGameActions(SessionService sessionService, BatailleCorseStateBroadcaster broadcaster) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
    }

    public void send(GameId gameId, PlayerId playerId)
            throws NotPlayersTurnException, FullCentralPileException, FinishedGameException {
        BatailleCorse game = sessionService.getGame(gameId, BatailleCorse.class);
        Player player = game.getPlayerByIndex(playerId.id());

        CardDto card = CardDto.from(player.getCardOnTop());
        game.send(player);
        sessionService.touch(gameId);

        broadcaster.broadcast(game, BatailleCorseEventType.SEND.toString(),
                new SendEventData(PlayerIdDto.from(player)),
                "Player " + player.id() + " sent " + card.name() + ".");
    }

    /** Returns whether the slap was valid; a wrong slap costs the slapper a penalty and is broadcast too. */
    public boolean slap(GameId gameId, PlayerId playerId)
            throws CannotSlapIfNoCardsInPileException, FinishedGameException {
        BatailleCorse game = sessionService.getGame(gameId, BatailleCorse.class);
        Player player = game.getPlayerByIndex(playerId.id());

        boolean successful = game.slap(player);
        sessionService.touch(gameId);

        String message = successful
                ? "Player " + player.id() + " slapped and won."
                : "Player " + player.id() + " slapped, lost, and received a penality.";
        broadcaster.broadcast(game, BatailleCorseEventType.SLAP.toString(),
                new SlapEventData(successful, PlayerIdDto.from(player)), message);
        return successful;
    }

    public void grab(GameId gameId, PlayerId playerId) throws CannotGrabException, FinishedGameException {
        BatailleCorse game = sessionService.getGame(gameId, BatailleCorse.class);
        Player player = game.getPlayerByIndex(playerId.id());

        game.grab(player);
        sessionService.touch(gameId);

        broadcaster.broadcast(game, BatailleCorseEventType.GRAB.toString(),
                new GrabEventData(PlayerIdDto.from(player)),
                "Player " + player.id() + " grabbed the pile. ");
    }
}
