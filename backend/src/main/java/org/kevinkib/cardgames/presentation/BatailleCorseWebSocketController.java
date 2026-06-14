package org.kevinkib.cardgames.presentation;

import java.util.HashMap;
import java.util.Map;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.bataillecorse.domain.Player;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.application.InvalidTokenException;
import org.kevinkib.cardgames.sessionmanagement.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionToken;
import org.kevinkib.cardgames.sessionmanagement.domain.GameMode;
import org.kevinkib.cardgames.presentation.api.CreateGamePayload;
import org.kevinkib.cardgames.presentation.api.ErrorResponse;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.api.SuccessResponse;
import org.kevinkib.cardgames.presentation.dto.*;
import org.kevinkib.cardgames.presentation.dto.event.*;
import org.kevinkib.cardgames.presentation.api.PresencePayload;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class BatailleCorseWebSocketController {

    public static final int NB_PLAYERS = 2;
    public static final String GAME_CREATED_MESSAGE = "Game created";

    private final SessionService sessionService;
    private final GameMessagingService gameMessagingService;
    private final DisconnectForfeitService disconnectForfeitService;

    public BatailleCorseWebSocketController(SessionService sessionService,
                                            GameMessagingService gameMessagingService,
                                            DisconnectForfeitService disconnectForfeitService) {
        this.sessionService = sessionService;
        this.gameMessagingService = gameMessagingService;
        this.disconnectForfeitService = disconnectForfeitService;
    }

    @MessageMapping("/create")
    @SendTo("/topic/game")
    public Response createGame(@Payload(required = false) CreateGamePayload payload) {
        GameMode mode = (payload != null && payload.mode() != null) ? payload.mode() : GameMode.SOLO;
        String name = (payload != null) ? payload.name() : null;

        BatailleCorse batailleCorse = (BatailleCorse) sessionService.createGame(NB_PLAYERS, mode, name);

        int seatsToReturn = (mode == GameMode.SOLO) ? NB_PLAYERS : 1;
        Map<Integer, String> tokens = new HashMap<>();
        for (int i = 0; i < seatsToReturn; i++) {
            SessionToken token = sessionService.loadTokenByPlayerId(batailleCorse.getId(), new PlayerId(i));
            tokens.put(i, token.uuid().toString());
        }

        return new SuccessResponse(
                EventType.CREATE.toString(),
                new CreateEventData(new BatailleCorseIdDto(batailleCorse.getId()), tokens),
                GAME_CREATED_MESSAGE,
                BatailleCorseDto.from(batailleCorse));
    }

    @MessageMapping("/send")
    public void send(GameActionPayload payload) {
        EventType eventType = EventType.SEND;

        GameId gameId = new GameId(payload.gameId());
        BatailleCorse batailleCorse = sessionService.getGame(gameId, BatailleCorse.class);

        Response response;
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                    .orElseThrow(InvalidTokenException::new);
            Player player = batailleCorse.getPlayerByIndex(playerId.id());

            CardDto cardDto = CardDto.from(player.getCardOnTop());
            batailleCorse.send(player);
            sessionService.touch(gameId);

            BatailleCorseDto batailleCorseDto = BatailleCorseDto.from(batailleCorse);
            String message = "Player " + player.id() + " sent " + cardDto.getName() + ".";
            SendEventData eventData = new SendEventData(PlayerIdDto.from(player));
            response = new SuccessResponse(eventType.toString(), eventData, message, batailleCorseDto);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            BatailleCorseDto batailleCorseDto = BatailleCorseDto.from(batailleCorse);
            response = new ErrorResponse(eventType.toString(), e.getMessage(), batailleCorseDto);
        }

        gameMessagingService.sendToGame(payload.gameId(), response);
    }

    @MessageMapping("/slap")
    public void slap(GameActionPayload payload) {
        EventType eventType = EventType.SLAP;

        GameId gameId = new GameId(payload.gameId());
        BatailleCorse batailleCorse = sessionService.getGame(gameId, BatailleCorse.class);

        Response response;
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                    .orElseThrow(InvalidTokenException::new);
            Player player = batailleCorse.getPlayerByIndex(playerId.id());

            boolean successfulSlap = batailleCorse.slap(player);
            sessionService.touch(gameId);
            String message = successfulSlap
                    ? "Player " + player.id() + " slapped and won."
                    : "Player " + player.id() + " slapped, lost, and received a penality.";

            BatailleCorseDto batailleCorseDto = BatailleCorseDto.from(batailleCorse);
            SlapEventData eventData = new SlapEventData(successfulSlap, PlayerIdDto.from(player));
            response = new SuccessResponse(eventType.toString(), eventData, message, batailleCorseDto);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            BatailleCorseDto batailleCorseDto = BatailleCorseDto.from(batailleCorse);
            response = new ErrorResponse(eventType.toString(), e.getMessage(), batailleCorseDto);
        }

        gameMessagingService.sendToGame(payload.gameId(), response);
    }

    @MessageMapping("/grab")
    public void grab(GameActionPayload payload) {
        EventType eventType = EventType.GRAB;

        GameId gameId = new GameId(payload.gameId());
        BatailleCorse batailleCorse = sessionService.getGame(gameId, BatailleCorse.class);

        Response response;
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                    .orElseThrow(InvalidTokenException::new);
            Player player = batailleCorse.getPlayerByIndex(playerId.id());

            batailleCorse.grab(player);
            sessionService.touch(gameId);

            BatailleCorseDto batailleCorseDto = BatailleCorseDto.from(batailleCorse);
            String message = "Player " + player.id() + " grabbed the pile. ";
            GrabEventData eventData = new GrabEventData(PlayerIdDto.from(player));
            response = new SuccessResponse(eventType.toString(), eventData, message, batailleCorseDto);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            BatailleCorseDto batailleCorseDto = BatailleCorseDto.from(batailleCorse);
            response = new ErrorResponse(eventType.toString(), e.getMessage(), batailleCorseDto);
        }

        gameMessagingService.sendToGame(payload.gameId(), response);
    }

    @MessageMapping("/presence")
    public void presence(@Payload PresencePayload payload, SimpMessageHeaderAccessor headers) {
        GameId gameId = new GameId(payload.gameId());
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                    .orElseThrow(InvalidTokenException::new);
            disconnectForfeitService.onPresence(headers.getSessionId(), gameId, playerId);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    @MessageMapping("/forfeit")
    public void forfeit(GameActionPayload payload) {
        GameId gameId = new GameId(payload.gameId());
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                    .orElseThrow(InvalidTokenException::new);
            disconnectForfeitService.forfeit(new Seat(gameId, playerId), ForfeitReason.RESIGNED);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    @MessageMapping("/rematch")
    public void rematch(GameActionPayload payload) {
        GameId gameId = new GameId(payload.gameId());
        try {
            PlayerId playerId = sessionService
                    .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                    .orElseThrow(InvalidTokenException::new);

            sessionService.getGameSession(gameId).requestRematch(playerId);

            Response response;
            if (sessionService.getGameSession(gameId).isRematchUnanimous()) {
                BatailleCorse fresh = (BatailleCorse) sessionService.rematch(gameId);
                response = new SuccessResponse(
                        EventType.REMATCH.toString(),
                        new RematchEventData(RematchStatus.STARTED, new PlayerIdDto(String.valueOf(playerId.id()))),
                        "Rematch started.",
                        BatailleCorseDto.from(fresh));
            } else {
                BatailleCorse current = sessionService.getGame(gameId, BatailleCorse.class);
                response = new SuccessResponse(
                        EventType.REMATCH.toString(),
                        new RematchEventData(RematchStatus.PENDING, new PlayerIdDto(String.valueOf(playerId.id()))),
                        "Rematch requested.",
                        BatailleCorseDto.from(current));
            }

            gameMessagingService.sendToGame(payload.gameId(), response);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}
