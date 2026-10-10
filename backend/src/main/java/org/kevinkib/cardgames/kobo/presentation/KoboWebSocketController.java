package org.kevinkib.cardgames.kobo.presentation;

import org.kevinkib.cardgames.kobo.domain.tableau.SlotRef;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cardgames.kobo.presentation.api.KoboCreatePayload;
import org.kevinkib.cardgames.kobo.presentation.api.KoboGiftPayload;
import org.kevinkib.cardgames.kobo.presentation.api.KoboMatchPayload;
import org.kevinkib.cardgames.kobo.presentation.api.KoboSlotPayload;
import org.kevinkib.cardgames.kobo.presentation.api.KoboSwapPayload;
import org.kevinkib.cardgames.kobo.presentation.api.KoboTargetPayload;
import org.kevinkib.cardgames.kobo.presentation.dto.event.KoboCreateEventData;
import org.kevinkib.cardgames.kobo.presentation.dto.event.KoboEventType;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.api.ErrorResponse;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * Kobo WebSocket endpoints, all under /kobo. They only authenticate the seat from its token and map
 * payloads onto domain arguments: every rule is in the Kobo aggregate. A rejected command answers
 * the sender alone with an ErrorResponse carrying its current state.
 */
@Controller
public class KoboWebSocketController {

    @FunctionalInterface
    private interface Action {
        void run(GameId gameId, PlayerId seat) throws Exception;
    }

    private final SessionService sessionService;
    private final KoboStateBroadcaster broadcaster;
    private final GameMessagingService messaging;
    private final KoboGameActions actions;

    public KoboWebSocketController(SessionService sessionService,
                                   KoboStateBroadcaster broadcaster,
                                   GameMessagingService messaging,
                                   KoboGameActions actions) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
        this.messaging = messaging;
        this.actions = actions;
    }

    @MessageMapping("/kobo/create")
    @SendTo("/topic/game")
    public Response createGame(@Payload(required = false) KoboCreatePayload payload) {
        String name = (payload != null) ? payload.name() : null;
        RoomCreated room = sessionService.createRoom(KoboFactory.GAME_TYPE, name);
        return new SuccessResponse(
                LifecycleEventType.CREATE.toString(),
                new KoboCreateEventData(room.gameId(), KoboFactory.GAME_TYPE, Map.of(0, room.hostToken())),
                "Room created",
                null);
    }

    @MessageMapping("/kobo/start")
    public void start(@Payload GameActionPayload payload) {
        handle(payload.gameId(), payload.token(), KoboEventType.START, (gameId, seat) -> {
            Kobo game = (Kobo) sessionService.startGame(gameId, payload.token());
            sessionService.touch(gameId);
            broadcaster.broadcast(game, KoboEventType.START.toString(), new EmptyEventData(), "Game started.");
        });
    }

    @MessageMapping("/kobo/ready")
    public void ready(@Payload GameActionPayload p) {
        handle(p.gameId(), p.token(), KoboEventType.READY, actions::ready);
    }

    @MessageMapping("/kobo/draw")
    public void draw(@Payload GameActionPayload p) {
        handle(p.gameId(), p.token(), KoboEventType.DRAW, actions::draw);
    }

    @MessageMapping("/kobo/swapDrawn")
    public void swapDrawn(@Payload KoboSlotPayload p) {
        handle(p.gameId(), p.token(), KoboEventType.SWAP, (id, seat) -> actions.swapDrawn(id, seat, p.slot()));
    }

    @MessageMapping("/kobo/discardDrawn")
    public void discardDrawn(@Payload GameActionPayload p) {
        handle(p.gameId(), p.token(), KoboEventType.DISCARD_DRAWN, actions::discardDrawn);
    }

    @MessageMapping("/kobo/skipPower")
    public void skipPower(@Payload GameActionPayload p) {
        handle(p.gameId(), p.token(), KoboEventType.SKIP_POWER, actions::skipPower);
    }

    @MessageMapping("/kobo/peek")
    public void peek(@Payload KoboTargetPayload p) {
        handle(p.gameId(), p.token(), KoboEventType.PEEK, (id, seat) -> actions.peek(id, seat, ref(p.seat(), p.slot())));
    }

    @MessageMapping("/kobo/blindSwap")
    public void blindSwap(@Payload KoboSwapPayload p) {
        handle(p.gameId(), p.token(), KoboEventType.BLIND_SWAP,
                (id, seat) -> actions.blindSwap(id, seat, p.ownSlot(), ref(p.seat(), p.slot())));
    }

    @MessageMapping("/kobo/kingSwap")
    public void kingSwap(@Payload KoboSlotPayload p) {
        handle(p.gameId(), p.token(), KoboEventType.KING_SWAP, (id, seat) -> actions.kingSwap(id, seat, p.slot()));
    }

    @MessageMapping("/kobo/endTurn")
    public void endTurn(@Payload GameActionPayload p) {
        handle(p.gameId(), p.token(), KoboEventType.END_TURN, actions::endTurn);
    }

    @MessageMapping("/kobo/announce")
    public void announce(@Payload GameActionPayload p) {
        handle(p.gameId(), p.token(), KoboEventType.KOBO, actions::announceKobo);
    }

    @MessageMapping("/kobo/match")
    public void match(@Payload KoboMatchPayload p) {
        handle(p.gameId(), p.token(), KoboEventType.MATCH, (id, seat) -> actions.match(id, seat,
                ref(p.seat(), p.slot()), p.giveSlot(), KoboRankMapper.toRank(p.expectedTopRank()),
                p.expectedRevision()));
    }

    @MessageMapping("/kobo/giveTen")
    public void giveTen(@Payload KoboGiftPayload p) {
        handle(p.gameId(), p.token(), KoboEventType.GIVE_TEN,
                (id, seat) -> actions.giveTen(id, seat, new PlayerId(p.seat())));
    }

    private static SlotRef ref(int seat, int slot) {
        return new SlotRef(new PlayerId(seat), slot);
    }

    private void handle(String rawGameId, String token, KoboEventType type, Action action) {
        GameId gameId;
        PlayerId seat;
        try {
            gameId = new GameId(rawGameId);
            seat = sessionService.findPlayerIdByToken(gameId, token).orElse(null);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return;
        }
        if (seat == null) {
            return;
        }
        try {
            action.run(gameId, seat);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            Object state = null;
            try {
                state = broadcaster.stateFor(sessionService.getGame(gameId, Kobo.class), seat);
            } catch (Exception ignored) {
                // no running game yet: the error carries no state
            }
            messaging.sendToSeat(gameId, seat, new ErrorResponse(type.toString(), e.getMessage(), state));
        }
    }
}
