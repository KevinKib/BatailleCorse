package org.kevinkib.cardgames.presentation.support;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;

/** Test fixture: a started room of a game type, every seat held by a human, through the public session use cases. */
public final class StartedRooms {

    private StartedRooms() { }

    /** Creates a room, seats {@code players} humans in it and starts it with the host's token. */
    public static Game start(SessionService sessions, String gameType, int players) {
        RoomCreated room = sessions.createRoom(gameType, "Player 1");
        GameId id = new GameId(room.gameId());
        for (int seat = 1; seat < players; seat++) {
            sessions.joinRoom(id, "Player " + (seat + 1));
        }
        return sessions.startGame(id, room.hostToken());
    }
}
