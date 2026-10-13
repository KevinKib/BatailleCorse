package org.kevinkib.cardgames.bataillecorse.presentation;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitLog;
import org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.presentation.dto.event.ForfeitEventData;
import org.kevinkib.cardgames.presentation.dto.event.OpponentDisconnectedEventData;
import org.kevinkib.cardgames.presentation.dto.event.OpponentReconnectedEventData;

public class BatailleCorseLifecycleBroadcaster implements GameLifecycleBroadcaster {

    private final BatailleCorseStateBroadcaster broadcaster;
    private final ForfeitLog forfeitLog;

    public BatailleCorseLifecycleBroadcaster(BatailleCorseStateBroadcaster broadcaster,
                                             ForfeitLog forfeitLog) {
        this.broadcaster = broadcaster;
        this.forfeitLog = forfeitLog;
    }

    @Override
    public boolean supports(Game game) {
        return game instanceof BatailleCorse;
    }

    @Override
    public void disconnected(Game game, PlayerId player, long deadlineEpochMs) {
        broadcaster.broadcast((BatailleCorse) game,
                LifecycleEventType.OPPONENT_DISCONNECTED.toString(),
                new OpponentDisconnectedEventData(player.id(), deadlineEpochMs),
                "Player " + player + " disconnected.");
    }

    @Override
    public void reconnected(Game game, PlayerId player) {
        broadcaster.broadcast((BatailleCorse) game,
                LifecycleEventType.OPPONENT_RECONNECTED.toString(),
                new OpponentReconnectedEventData(player.id()),
                "Player " + player + " reconnected.");
    }

    @Override
    public void forfeited(Game game, PlayerId player, ForfeitReason reason) {
        BatailleCorse bc = (BatailleCorse) game;
        broadcaster.broadcast(bc,
                LifecycleEventType.FORFEIT.toString(),
                new ForfeitEventData(player.id(), reason.name()),
                "Player " + player + " forfeited.",
                BatailleCorseDto.from(bc, forfeitLog.reasonsBySeat(game.getId())));
    }
}
