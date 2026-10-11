package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameMode;
import org.kevinkib.cardgames.sessionmanagement.core.domain.NoFreeSeatException;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SeatTakenException;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionPlayer;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionToken;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    /** Host-only: fills the lowest free seat of a not-yet-started room with a computer player. */
    public PlayerId addBot(GameId id, String hostToken) {
        SessionGame lobby = openLobbyOwnedBy(id, hostToken);
        try {
            return lobby.claimBot(null).id();
        } catch (NoFreeSeatException e) {
            throw new RoomFullException(id);
        }
    }

    /** Host-only: frees a bot seat of a not-yet-started room (see {@link SessionGame#removeBot}). */
    public void removeBot(GameId id, String hostToken, int seat) {
        SessionGame lobby = openLobbyOwnedBy(id, hostToken);
        lobby.removeBot(new PlayerId(seat));
    }

    /**
     * Host-only: merges {@code changes} into the options of a not-yet-started room. The whole merged map is
     * validated by the game (an unknown key or value is refused and nothing changes), then stored with the
     * defaults filled. Returns the per-viewer lobby views, computed in the same critical section.
     */
    public synchronized List<LobbyView> updateOptions(GameId id, String hostToken, Map<String, String> changes) {
        SessionGame lobby = openLobbyOwnedBy(id, hostToken);
        Map<String, String> merged = new HashMap<>(lobby.options().values());
        merged.putAll(changes);
        GameOptions validated = gameFactories.validate(lobby.gameType(), GameOptions.of(merged));
        repository.saveLobby(new SessionGame(lobby.id(), lobby.gameType(), validated, lobby.players()));
        return lobbyViews(id);
    }

    /**
     * Occupant-only: renames the caller's own seat in a not-yet-started room (see {@link SessionGame#renameSeat}).
     * Returns the per-viewer lobby views, computed in the same critical section.
     */
    public synchronized List<LobbyView> renameSeat(GameId id, String token, String name) {
        if (repository.findGame(id).isPresent()) {
            throw new GameAlreadyStartedException(id);
        }
        SessionGame lobby = repository.loadSessionGame(id);
        PlayerId actor = lobby.findPlayerByToken(new SessionToken(token)).orElseThrow(InvalidTokenException::new);
        lobby.renameSeat(actor, name);
        return lobbyViews(id);
    }

    /**
     * Host-only: sets one option on a computer seat of a not-yet-started room. The game validates the key and
     * the value (strictly); a refused update leaves the seat unchanged. Returns the per-viewer lobby views.
     */
    public synchronized List<LobbyView> setSeatOption(GameId id, String hostToken, int seat, String key, String value) {
        SessionGame lobby = openLobbyOwnedBy(id, hostToken);
        gameFactories.validateSeatOption(lobby.gameType(), key, value);
        lobby.setSeatOption(new PlayerId(seat), key, value);
        return lobbyViews(id);
    }

    /** The options set on a seat (empty for a human or a free seat); answers the {@code BotSeatOptions} port. */
    public Map<String, String> seatOptions(GameId id, PlayerId seat) {
        SessionPlayer player = repository.loadSessionGame(id).players().get(seat);
        return player == null ? Map.of() : player.options();
    }

    /** Seats held by computer players, for whoever must drive them once the game runs. */
    public Set<PlayerId> botSeats(GameId id) {
        return repository.loadSessionGame(id).botSeats();
    }

    private SessionGame openLobbyOwnedBy(GameId id, String hostToken) {
        if (repository.findGame(id).isPresent()) {
            throw new GameAlreadyStartedException(id);
        }
        SessionGame lobby = repository.loadSessionGame(id);
        PlayerId actor = lobby.findPlayerByToken(new SessionToken(hostToken))
                .orElseThrow(() -> new NotHostException(id));
        if (!lobby.isHost(actor)) {
            throw new NotHostException(id);
        }
        return lobby;
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

    /**
     * Records this seat's rematch request; returns true when every expected player has requested
     * and enough of them remain to play (see {@link SessionGame#isRematchUnanimous}).
     */
    public synchronized boolean requestRematch(GameId id, PlayerId playerId) {
        SessionGame session = repository.loadSessionGame(id);
        session.requestRematch(playerId);
        return isRematchReady(session);
    }

    /**
     * A player leaves the rematch for good (grace delay over, or explicit quit). Nobody waits for
     * them any more: if every other expected player had already asked, the rematch starts now and
     * the fresh game is returned. Empty when the game is not over, or when the rematch is still
     * waiting for someone, or cannot be played any more (too few players left).
     */
    public synchronized Optional<Game> leaveRematch(GameId id, PlayerId playerId) {
        if (repository.findGame(id).filter(Game::isFinished).isEmpty()) {
            return Optional.empty();
        }
        SessionGame session = repository.loadSessionGame(id);
        session.leaveRematch(playerId);
        return isRematchReady(session) ? Optional.of(rematch(id)) : Optional.empty();
    }

    private boolean isRematchReady(SessionGame session) {
        return session.isRematchUnanimous()
                && session.isRematchPlayable(gameFactories.minPlayers(session.gameType()));
    }

    public Game rematch(GameId id) {
        SessionGame session = repository.loadSessionGame(id);
        session.releaseDepartedSeats();
        // Deal the rematch to the players who actually joined, not every room seat (matches startGame).
        Game fresh = gameFactories.factoryFor(session.gameType()).create(id, session.claimedCount(), session.options());
        session.clearRematch();
        repository.save(fresh, session);
        return fresh;
    }

    public List<SeatView> seats(GameId gameId) {
        return repository.loadSessionGame(gameId).seats().stream()
                .map(s -> new SeatView(s.id().id(), s.name(), s.isClaimed(), s.isBot()))
                .toList();
    }

    /** Display name of every claimed seat; free seats and seats without a name are absent. */
    public java.util.Map<PlayerId, String> seatNames(GameId gameId) {
        java.util.Map<PlayerId, String> names = new java.util.HashMap<>();
        for (SeatView seat : seats(gameId)) {
            if (seat.joined() && seat.name() != null) {
                names.put(new PlayerId(seat.seat()), seat.name());
            }
        }
        return names;
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

    @Override
    public Optional<PlayerId> findPlayerIdByToken(GameId gameId, String token) {
        return repository.loadSessionGame(gameId).findPlayerByToken(new SessionToken(token));
    }

    public LobbyView lobbyView(GameId id, String token) {
        SessionGame lobby = repository.loadSessionGame(id);
        PlayerId viewer = lobby.findPlayerByToken(new SessionToken(token))
                .orElseThrow(InvalidTokenException::new);
        return LobbyView.forViewer(lobby, minPlayers(lobby.gameType()), maxPlayers(lobby.gameType()),
                gameFactories.optionDescriptors(lobby.gameType()),
                gameFactories.seatOptionDescriptors(lobby.gameType()), viewer);
    }

    public List<LobbyView> lobbyViews(GameId id) {
        SessionGame lobby = repository.loadSessionGame(id);
        int min = minPlayers(lobby.gameType());
        int max = maxPlayers(lobby.gameType());
        List<OptionDescriptor> descriptors = gameFactories.optionDescriptors(lobby.gameType());
        List<OptionDescriptor> seatDescriptors = gameFactories.seatOptionDescriptors(lobby.gameType());
        return lobby.seats().stream()
                .filter(seat -> seat.isClaimed() && !seat.isBot())
                .map(seat -> LobbyView.forViewer(lobby, min, max, descriptors, seatDescriptors, seat.id()))
                .toList();
    }
}
