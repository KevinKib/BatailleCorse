package org.kevinkib.cardgames.blackjack.presentation;

import org.kevinkib.cardgames.blackjack.domain.ActionNotAllowedException;
import org.kevinkib.cardgames.blackjack.domain.AlreadyBetException;
import org.kevinkib.cardgames.blackjack.domain.Blackjack;
import org.kevinkib.cardgames.blackjack.domain.BlackjackException;
import org.kevinkib.cardgames.blackjack.domain.BlackjackFactory;
import org.kevinkib.cardgames.blackjack.domain.EliminatedSeatException;
import org.kevinkib.cardgames.blackjack.domain.FinishedGameException;
import org.kevinkib.cardgames.blackjack.domain.InvalidBetException;
import org.kevinkib.cardgames.blackjack.domain.NotPlayersTurnException;
import org.kevinkib.cardgames.blackjack.domain.WrongPhaseException;
import org.kevinkib.cardgames.blackjack.presentation.api.BlackjackBetPayload;
import org.kevinkib.cardgames.blackjack.presentation.api.BlackjackErrorResponse;
import org.kevinkib.cardgames.blackjack.presentation.dto.BlackjackDto;
import org.kevinkib.cardgames.blackjack.presentation.dto.event.BlackjackEventType;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * Blackjack WebSocket endpoints, all under /blackjack. They only authenticate the seat from its token and map
 * payloads onto domain arguments: every rule is in the {@link Blackjack} aggregate. A rejected command answers the
 * sender alone with an error carrying a stable code (the client words it) and its current state.
 */
@Controller
public class BlackjackWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(BlackjackWebSocketController.class);

    private final SessionService sessionService;
    private final SeatStateBroadcaster<Blackjack, BlackjackDto> broadcaster;
    private final SeatCommands seatCommands;
    private final BlackjackGameActions actions;
    private final GameMessagingService messaging;

    public BlackjackWebSocketController(SessionService sessionService,
                                        SeatStateBroadcaster<Blackjack, BlackjackDto> broadcaster,
                                        SeatCommands seatCommands,
                                        BlackjackGameActions actions,
                                        GameMessagingService messaging) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
        this.seatCommands = seatCommands;
        this.actions = actions;
        this.messaging = messaging;
    }

    @MessageMapping("/blackjack/start")
    public void start(@Payload GameActionPayload payload) {
        handle(payload.gameId(), payload.token(), BlackjackEventType.START, (gameId, seat) -> {
            if (!BlackjackFactory.GAME_TYPE.equals(sessionService.gameType(gameId))) {
                throw new IllegalArgumentException("This room is not a Blackjack game");
            }
            Blackjack game = sessionService.startGame(gameId, payload.token(), Blackjack.class);
            sessionService.touch(gameId);
            broadcaster.broadcast(game, BlackjackEventType.START.toString(), new EmptyEventData(), "Game started.");
        });
    }

    @MessageMapping("/blackjack/bet")
    public void bet(@Payload BlackjackBetPayload p) {
        handle(p.gameId(), p.token(), BlackjackEventType.BET, (id, seat) -> {
            if (p.amount() == null) {
                throw new IllegalArgumentException("Missing field: amount");
            }
            command(BlackjackEventType.BET, id, seat, () -> actions.bet(id, seat, p.amount()));
        });
    }

    @MessageMapping("/blackjack/hit")
    public void hit(@Payload GameActionPayload p) {
        handle(p.gameId(), p.token(), BlackjackEventType.HIT,
                (id, seat) -> command(BlackjackEventType.HIT, id, seat, () -> actions.hit(id, seat)));
    }

    @MessageMapping("/blackjack/stand")
    public void stand(@Payload GameActionPayload p) {
        handle(p.gameId(), p.token(), BlackjackEventType.STAND,
                (id, seat) -> command(BlackjackEventType.STAND, id, seat, () -> actions.stand(id, seat)));
    }

    @MessageMapping("/blackjack/double")
    public void doubleDown(@Payload GameActionPayload p) {
        handle(p.gameId(), p.token(), BlackjackEventType.DOUBLE,
                (id, seat) -> command(BlackjackEventType.DOUBLE, id, seat, () -> actions.doubleDown(id, seat)));
    }

    @MessageMapping("/blackjack/split")
    public void split(@Payload GameActionPayload p) {
        handle(p.gameId(), p.token(), BlackjackEventType.SPLIT,
                (id, seat) -> command(BlackjackEventType.SPLIT, id, seat, () -> actions.split(id, seat)));
    }

    /** Runs a command; a domain refusal is answered to the sender alone with its code, anything else propagates. */
    private void command(BlackjackEventType type, GameId id, PlayerId seat, Runnable0 run) {
        try {
            run.run();
        } catch (BlackjackException e) {
            log.debug("{} refused in game {}: {}", type, id, e.getMessage());
            Object state = null;
            try {
                state = broadcaster.stateFor(sessionService.getGame(id, Blackjack.class), seat);
            } catch (RuntimeException ignored) {
                // no state to attach: the error alone is still useful
            }
            messaging.sendToSeat(id, seat, new BlackjackErrorResponse(type.toString(), e.getMessage(), codeOf(e), state));
        }
    }

    @FunctionalInterface
    private interface Runnable0 {
        void run() throws BlackjackException;
    }

    /** The stable code of a refusal, the client's only handle on it. */
    static String codeOf(BlackjackException e) {
        if (e instanceof NotPlayersTurnException || e instanceof EliminatedSeatException) {
            return "NOT_YOUR_TURN";
        } else if (e instanceof WrongPhaseException) {
            return "WRONG_PHASE";
        } else if (e instanceof AlreadyBetException) {
            return "ALREADY_BET";
        } else if (e instanceof InvalidBetException) {
            return "INVALID_BET";
        } else if (e instanceof ActionNotAllowedException) {
            return "ACTION_NOT_ALLOWED";
        } else if (e instanceof FinishedGameException) {
            return "GAME_FINISHED";
        }
        return "ACTION_NOT_ALLOWED";
    }

    private void handle(String rawGameId, String token, BlackjackEventType type, SeatCommands.SeatAction action) {
        seatCommands.handle(rawGameId, token, type.toString(), action,
                (gameId, seat) -> broadcaster.stateFor(sessionService.getGame(gameId, Blackjack.class), seat));
    }
}
