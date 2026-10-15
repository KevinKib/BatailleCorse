package org.kevinkib.cardgames.president.presentation;

import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.PresidentFactory;
import org.kevinkib.cardgames.president.presentation.api.PresidentCardsPayload;
import org.kevinkib.cardgames.president.presentation.dto.PresidentDto;
import org.kevinkib.cardgames.president.presentation.dto.event.PresidentEventType;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cardgames.presentation.dto.CardLabels;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cards.domain.Card;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * President WebSocket endpoints, all under /president. They only authenticate the seat from its token and map
 * payloads onto domain arguments: every rule is in the President aggregate. A rejected command answers the
 * sender alone with an ErrorResponse carrying its current state.
 */
@Controller
public class PresidentWebSocketController {

    private final SessionService sessionService;
    private final SeatStateBroadcaster<President, PresidentDto> broadcaster;
    private final SeatCommands seatCommands;
    private final PresidentGameActions actions;

    public PresidentWebSocketController(SessionService sessionService,
                                        SeatStateBroadcaster<President, PresidentDto> broadcaster,
                                        SeatCommands seatCommands,
                                        PresidentGameActions actions) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
        this.seatCommands = seatCommands;
        this.actions = actions;
    }

    @MessageMapping("/president/start")
    public void start(@Payload GameActionPayload payload) {
        handle(payload.gameId(), payload.token(), PresidentEventType.START, (gameId, seat) -> {
            if (!PresidentFactory.GAME_TYPE.equals(sessionService.gameType(gameId))) {
                throw new IllegalArgumentException("This room is not a President game");
            }
            President game = sessionService.startGame(gameId, payload.token(), President.class);
            sessionService.touch(gameId);
            broadcaster.broadcast(game, PresidentEventType.START.toString(), new EmptyEventData(), "Game started.");
        });
    }

    @MessageMapping("/president/play")
    public void play(@Payload PresidentCardsPayload p) {
        handle(p.gameId(), p.token(), PresidentEventType.PLAY,
                (id, seat) -> actions.play(id, seat, cardsOf(p.cards())));
    }

    @MessageMapping("/president/pass")
    public void pass(@Payload GameActionPayload p) {
        handle(p.gameId(), p.token(), PresidentEventType.PASS, actions::pass);
    }

    @MessageMapping("/president/giveBack")
    public void giveBack(@Payload PresidentCardsPayload p) {
        handle(p.gameId(), p.token(), PresidentEventType.EXCHANGE,
                (id, seat) -> actions.giveBack(id, seat, cardsOf(p.cards())));
    }

    @MessageMapping("/president/ready")
    public void ready(@Payload GameActionPayload p) {
        handle(p.gameId(), p.token(), PresidentEventType.READY, actions::ready);
    }

    private static List<Card> cardsOf(List<CardDto> cards) {
        if (cards == null || cards.isEmpty()) {
            throw new IllegalArgumentException("Missing field: cards");
        }
        return cards.stream().map(CardLabels::card).toList();
    }

    private void handle(String rawGameId, String token, PresidentEventType type, SeatCommands.SeatAction action) {
        seatCommands.handle(rawGameId, token, type.toString(), action,
                (gameId, seat) -> broadcaster.stateFor(sessionService.getGame(gameId, President.class), seat));
    }
}
