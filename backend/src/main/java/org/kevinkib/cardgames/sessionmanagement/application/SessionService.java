package org.kevinkib.cardgames.sessionmanagement.application;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.domain.GameMode;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionPlayer;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionToken;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public class SessionService {

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
            for (PlayerId playerId : game.getPlayerIds()) {
                sessionGame.claim(playerId, defaultNameFor(playerId));
            }
        } else {
            PlayerId creatorSeat = new PlayerId(0);
            sessionGame.claim(creatorSeat, resolveName(creatorSeat, creatorName));
        }

        repository.save(game, sessionGame);

        return game;
    }

    public JoinResult joinGame(GameId gameId) {
        return joinGame(gameId, null);
    }

    public JoinResult joinGame(GameId gameId, String name) {
        SessionGame sessionGame = repository.loadSessionGame(gameId);

        if (sessionGame.isClaimed(JOINER_SEAT)) {
            throw new SeatUnavailableException(JOINER_SEAT);
        }

        sessionGame.claim(JOINER_SEAT, resolveName(JOINER_SEAT, name));
        SessionToken token = sessionGame.findTokenByPlayer(JOINER_SEAT)
                .orElseThrow(() -> new IllegalStateException("Seat " + JOINER_SEAT.id() + " has no token"));

        return new JoinResult(JOINER_SEAT, token);
    }

    public JoinResult joinRoom(GameId id, String name) {
        if (repository.findGame(id).isPresent()) {
            throw new GameAlreadyStartedException(id);
        }
        SessionGame lobby = repository.loadSessionGame(id);
        PlayerId free = lobby.seats().stream()
                .filter(seat -> !seat.isClaimed())
                .map(SessionPlayer::id)
                .findFirst()
                .orElseThrow(() -> new RoomFullException(id));
        lobby.claim(free, resolveName(free, name));
        SessionToken token = lobby.findTokenByPlayer(free)
                .orElseThrow(() -> new IllegalStateException("Seat " + free.id() + " has no token"));
        return new JoinResult(free, token);
    }

    public SessionGame createRoom(String gameType, String hostName) {
        GameId id = GameId.generate();
        int max = gameFactories.maxPlayers(gameType);
        List<PlayerId> seats = IntStream.range(0, max).mapToObj(PlayerId::new).toList();
        SessionGame lobby = SessionGame.create(id, seats, gameType);
        PlayerId host = new PlayerId(0);
        lobby.claim(host, resolveName(host, hostName));
        repository.saveLobby(lobby);
        return lobby;
    }

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
        Game fresh = gameFactories.factoryFor(session.gameType()).create(id, session.seats().size());
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

    public void touch(GameId id) {
        repository.touch(id);
    }

    public SessionToken loadTokenByPlayerId(GameId gameId, PlayerId playerId) {
        return repository.loadSessionToken(gameId, playerId);
    }

    public Optional<PlayerId> findPlayerIdByToken(GameId gameId, SessionToken token) {
        return repository.loadSessionGame(gameId).findPlayerByToken(token);
    }

    private String resolveName(PlayerId seat, String provided) {
        if (provided == null || provided.isBlank()) {
            return defaultNameFor(seat);
        }
        return provided.trim();
    }

    private String defaultNameFor(PlayerId seat) {
        return "Player " + (seat.id() + 1);
    }
}
