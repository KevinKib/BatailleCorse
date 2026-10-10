package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameMode;
import org.kevinkib.cardgames.sessionmanagement.core.domain.NoFreeSeatException;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SeatTakenException;
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
        try {
            SessionPlayer claimed = sessionGame.claimSeat(JOINER_SEAT, name);
            return new JoinResult(claimed.id(), claimed.token().uuid().toString());
        } catch (SeatTakenException e) {
            throw new SeatUnavailableException(JOINER_SEAT);
        }
    }

    public JoinResult joinRoom(GameId id, String name) {
        if (repository.findGame(id).isPresent()) {
            throw new GameAlreadyStartedException(id);
        }
        SessionGame lobby = repository.loadSessionGame(id);
        try {
            SessionPlayer claimed = lobby.claimNextFreeSeat(name);
            return new JoinResult(claimed.id(), claimed.token().uuid().toString());
        } catch (NoFreeSeatException e) {
            throw new RoomFullException(id);
        }
    }

    /**
     * Re-join the room for a rematch. If a game is present (finished or not), the room is first
     * reopened — the game is dropped and the lobby reset to empty — then a seat is claimed; the
     * first caller takes seat 0 and becomes host. Synchronized so a burst of simultaneous
     * Play-Again calls reopen exactly once before any of them claims a seat.
     */
    public synchronized JoinResult playAgain(GameId id, String name) {
        SessionGame existing = repository.loadSessionGame(id);
        String type = existing.gameType();
        GameOptions options = existing.options();
        if (repository.findGame(id).isPresent()) {
            repository.remove(id);
            repository.saveLobby(SessionGame.create(id, gameFactories.maxPlayers(type), type, options));
        }
        return joinRoom(id, name);
    }

    public RoomCreated createRoom(String gameType, String hostName) {
        return createRoom(gameType, hostName, GameOptions.none());
    }

    public RoomCreated createRoom(String gameType, String hostName, GameOptions options) {
        GameId id = GameId.generate();
        SessionGame lobby = SessionGame.create(id, gameFactories.maxPlayers(gameType), gameType, options);
        lobby.claimHost(hostName);
        repository.saveLobby(lobby);
        SessionToken hostToken = lobby.findTokenByPlayer(new PlayerId(0))
                .orElseThrow(() -> new IllegalStateException("Host seat has no token"));
        return new RoomCreated(id.uuid().toString(), hostToken.uuid().toString());
    }

    public String gameType(GameId id) {
        return repository.loadSessionGame(id).gameType();
    }

    public Game startGame(GameId id, String hostToken) {
        if (repository.findGame(id).isPresent()) {
            throw new GameAlreadyStartedException(id);
        }
        SessionGame lobby = repository.loadSessionGame(id);
        PlayerId actor = lobby.findPlayerByToken(new SessionToken(hostToken))
                .orElseThrow(() -> new NotHostException(id));
        if (!lobby.isHost(actor)) {
            throw new NotHostException(id);
        }
        int claimed = lobby.claimedCount();
        int min = gameFactories.minPlayers(lobby.gameType());
        if (claimed < min) {
            throw new NotEnoughPlayersException(id, claimed, min);
        }
        Game game = gameFactories.factoryFor(lobby.gameType()).create(id, claimed, lobby.options());
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

    /** Records this seat's rematch request; returns true when all seats have requested. */
    public boolean requestRematch(GameId id, PlayerId playerId) {
        SessionGame session = repository.loadSessionGame(id);
        session.requestRematch(playerId);
        return session.isRematchUnanimous();
    }

    public Game rematch(GameId id) {
        SessionGame session = repository.loadSessionGame(id);
        // Deal the rematch to the players who actually joined, not every room seat (matches startGame).
        Game fresh = gameFactories.factoryFor(session.gameType()).create(id, session.claimedCount(), session.options());
        session.clearRematch();
        repository.save(fresh, session);
        return fresh;
    }

    public List<SeatView> seats(GameId gameId) {
        return repository.loadSessionGame(gameId).seats().stream()
                .map(s -> new SeatView(s.id().id(), s.name(), s.isClaimed()))
                .toList();
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

    public String tokenForSeat(GameId gameId, PlayerId playerId) {
        return repository.loadSessionToken(gameId, playerId).uuid().toString();
    }

    public Optional<PlayerId> findPlayerIdByToken(GameId gameId, String token) {
        return repository.loadSessionGame(gameId).findPlayerByToken(new SessionToken(token));
    }

    public LobbyView lobbyView(GameId id, String token) {
        SessionGame lobby = repository.loadSessionGame(id);
        PlayerId viewer = lobby.findPlayerByToken(new SessionToken(token))
                .orElseThrow(InvalidTokenException::new);
        return LobbyView.forViewer(lobby, minPlayers(lobby.gameType()), maxPlayers(lobby.gameType()), viewer);
    }

    public List<LobbyView> lobbyViews(GameId id) {
        SessionGame lobby = repository.loadSessionGame(id);
        int min = minPlayers(lobby.gameType());
        int max = maxPlayers(lobby.gameType());
        return lobby.seats().stream()
                .filter(SessionPlayer::isClaimed)
                .map(seat -> LobbyView.forViewer(lobby, min, max, seat.id()))
                .toList();
    }
}
