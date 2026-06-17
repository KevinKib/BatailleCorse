package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.core.domain.GameMode;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionPlayer;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionToken;

import java.util.List;
import java.util.Optional;

public class SessionService implements GameDirectory {

    private static final PlayerId JOINER_SEAT = new PlayerId(1);

    private final SessionRepository repository;
    private final GameFactories gameFactories;

    public SessionService(SessionRepository repository, GameFactories gameFactories) {
        this.repository = repository;
        this.gameFactories = gameFactories;
    }

    public Game createGame(String gameType, int nbPlayers) {
        return createGame(gameType, nbPlayers, GameMode.SOLO, null);
    }

    public Game createGame(String gameType, int nbPlayers, GameMode mode) {
        return createGame(gameType, nbPlayers, mode, null);
    }

    public Game createGame(String gameType, int nbPlayers, GameMode mode, String creatorName) {
        GameId id = GameId.generate();
        Game game = gameFactories.factoryFor(gameType).create(id, nbPlayers);

        SessionGame sessionGame = SessionGame.create(id, game.getPlayerIds(), gameType);

        if (mode == GameMode.SOLO) {
            sessionGame.claimAllSeats();
        } else {
            sessionGame.claimHost(creatorName);
        }

        repository.save(game, sessionGame);

        return game;
    }

    public JoinResult joinGame(GameId gameId) {
        return joinGame(gameId, null);
    }

    public JoinResult joinGame(GameId gameId, String name) {
        SessionGame sessionGame = repository.loadSessionGame(gameId);
        SessionPlayer claimed = sessionGame.claimSeat(JOINER_SEAT, name);
        return new JoinResult(claimed.id(), claimed.token());
    }

    public JoinResult joinRoom(GameId id, String name) {
        if (repository.findGame(id).isPresent()) {
            throw new GameAlreadyStartedException(id);
        }
        SessionGame lobby = repository.loadSessionGame(id);
        SessionPlayer claimed = lobby.claimNextFreeSeat(name);
        return new JoinResult(claimed.id(), claimed.token());
    }

    public SessionGame createRoom(String gameType, String hostName) {
        GameId id = GameId.generate();
        SessionGame lobby = SessionGame.create(id, gameFactories.maxPlayers(gameType), gameType);
        lobby.claimHost(hostName);
        repository.saveLobby(lobby);
        return lobby;
    }

    public Game startGame(GameId id, SessionToken hostToken) {
        if (repository.findGame(id).isPresent()) {
            throw new GameAlreadyStartedException(id);
        }
        SessionGame lobby = repository.loadSessionGame(id);
        PlayerId actor = lobby.findPlayerByToken(hostToken)
                .orElseThrow(() -> new NotHostException(id));
        if (!lobby.isHost(actor)) {
            throw new NotHostException(id);
        }
        int claimed = lobby.claimedCount();
        int min = gameFactories.minPlayers(lobby.gameType());
        if (claimed < min) {
            throw new NotEnoughPlayersException(id, claimed, min);
        }
        Game game = gameFactories.factoryFor(lobby.gameType()).create(id, claimed);
        repository.save(game, lobby);
        return game;
    }

    @Override
    public Optional<Game> findGame(GameId id) {
        return repository.findGame(id);
    }

    public int minPlayers(String gameType) {
        return gameFactories.minPlayers(gameType);
    }

    public int maxPlayers(String gameType) {
        return gameFactories.maxPlayers(gameType);
    }

    public SessionGame getGameSession(GameId id) {
        return repository.loadSessionGame(id);
    }

    public Game rematch(GameId id) {
        SessionGame session = repository.loadSessionGame(id);
        Game fresh = gameFactories.factoryFor(session.gameType()).create(id, session.seatCount());
        session.clearRematch();
        repository.save(fresh, session);
        return fresh;
    }

    public List<SessionPlayer> getSeats(GameId gameId) {
        return repository.loadSessionGame(gameId).seats();
    }

    public boolean isSeatClaimed(GameId gameId, PlayerId playerId) {
        return repository.loadSessionGame(gameId).isClaimed(playerId);
    }

    public Game getGame(GameId id) throws InvalidGameIdException {
        try {
            return repository.load(id);
        } catch (IllegalArgumentException e) {
            throw new InvalidGameIdException(id);
        }
    }

    public <T extends Game> T getGame(GameId id, Class<T> type) throws InvalidGameIdException {
        Game game = getGame(id);
        if (!type.isInstance(game)) {
            throw new IllegalStateException(
                    "Game " + id + " is " + game.getClass().getSimpleName() + ", not " + type.getSimpleName());
        }
        return type.cast(game);
    }

    @Override
    public void touch(GameId id) {
        repository.touch(id);
    }

    public SessionToken loadTokenByPlayerId(GameId gameId, PlayerId playerId) {
        return repository.loadSessionToken(gameId, playerId);
    }

    public Optional<PlayerId> findPlayerIdByToken(GameId gameId, SessionToken token) {
        return repository.loadSessionGame(gameId).findPlayerByToken(token);
    }
}
