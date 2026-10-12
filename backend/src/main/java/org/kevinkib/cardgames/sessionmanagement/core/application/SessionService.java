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
import java.util.function.Consumer;

/**
 * Session use cases. Every operation that reads or mutates a lobby's seats or options, and the computation of the
 * per-viewer views with their tokens, runs under the monitor of this service (one lock for all lobbies: traffic is
 * tiny, and a per-game lock can replace it later without changing a caller). Each mutation of a lobby increments
 * its version, carried by every view, so a client can drop a view older than the one it holds.
 */
public class SessionService implements GameDirectory {

    private static final PlayerId JOINER_SEAT = new PlayerId(1);

    private final SessionRepository repository;
    private final GameFactories gameFactories;
    private volatile Consumer<GameId> lobbyRemovalListener = removed -> { };

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

    public synchronized JoinResult joinGame(GameId gameId, String name) {
        SessionGame sessionGame = repository.loadSessionGame(gameId);
        try {
            SessionPlayer claimed = sessionGame.claimSeat(JOINER_SEAT, name);
            return new JoinResult(claimed.id(), claimed.token().uuid().toString());
        } catch (SeatTakenException e) {
            throw new SeatUnavailableException(JOINER_SEAT);
        }
    }

    public synchronized JoinResult joinRoom(GameId id, String name) {
        if (repository.findGame(id).isPresent()) {
            throw new GameAlreadyStartedException(id);
        }
        SessionGame lobby = repository.loadSessionGame(id);
        try {
            SessionPlayer claimed = lobby.claimNextFreeSeat(name);
            lobby.bumpVersion();
            return new JoinResult(claimed.id(), claimed.token().uuid().toString(), lobbyViews(id));
        } catch (NoFreeSeatException e) {
            throw new RoomFullException(id);
        }
    }

    /**
     * Re-join the room for a rematch. If a game is present (finished or not), the room is first
     * reopened — the game is dropped and the lobby reset, keeping the computer players (order, names and seat
     * options) on the seats right after seat 0 — then a seat is claimed; the first caller takes seat 0 and becomes host. Synchronized so a burst of simultaneous
     * Play-Again calls reopen exactly once before any of them claims a seat.
     */
    public synchronized JoinResult playAgain(GameId id, String name) {
        SessionGame existing = repository.loadSessionGame(id);
        String type = existing.gameType();
        GameOptions options = existing.options();
        if (repository.findGame(id).isPresent()) {
            repository.remove(id);
            SessionGame fresh = SessionGame.create(id, gameFactories.maxPlayers(type), type, options);
            fresh.claimBotsOf(existing);
            // The reopened lobby continues the version of the one it replaces: a client never sees it go backwards.
            repository.saveLobby(new SessionGame(id, type, options, fresh.players(), existing.version()));
        }
        return joinRoom(id, name);
    }

    public RoomCreated createRoom(String gameType, String hostName) {
        return createRoom(gameType, hostName, GameOptions.none());
    }

    public synchronized RoomCreated createRoom(String gameType, String hostName, GameOptions options) {
        GameId id = GameId.generate();
        SessionGame lobby = SessionGame.create(id, gameFactories.maxPlayers(gameType), gameType, options);
        lobby.claimHost(hostName);
        repository.saveLobby(lobby);
        SessionToken hostToken = lobby.findTokenByPlayer(new PlayerId(0))
                .orElseThrow(() -> new IllegalStateException("Host seat has no token"));
        return new RoomCreated(id.uuid().toString(), hostToken.uuid().toString());
    }

    /** Host-only: fills the lowest free seat of a not-yet-started room with a computer player. */
    public synchronized PlayerId addBot(GameId id, String hostToken) {
        SessionGame lobby = openLobbyOwnedBy(id, hostToken);
        if (!gameFactories.supportsBots(lobby.gameType())) {
            throw new BotsNotSupportedException(id, lobby.gameType());
        }
        try {
            PlayerId bot = lobby.claimBot(null).id();
            lobby.bumpVersion();
            return bot;
        } catch (NoFreeSeatException e) {
            throw new RoomFullException(id);
        }
    }

    /** {@link #addBot} and the resulting views, in one critical section. */
    public synchronized List<AddressedLobbyView> addBotViews(GameId id, String hostToken) {
        addBot(id, hostToken);
        return lobbyViews(id);
    }

    /** {@link #removeBot} and the resulting views, in one critical section. */
    public synchronized List<AddressedLobbyView> removeBotViews(GameId id, String hostToken, int seat) {
        removeBot(id, hostToken, seat);
        return lobbyViews(id);
    }

    /**
     * Host-only: removes another human from a not-yet-started room, connected or not (see
     * {@link SessionGame#removeHuman}). Returns the removed player's token and the remaining humans' views.
     */
    public synchronized SeatRemoval removeSeat(GameId id, String hostToken, int seat) {
        SessionGame lobby = openLobbyOwnedBy(id, hostToken);
        SessionToken removed = lobby.removeHuman(new PlayerId(seat));
        lobby.bumpVersion();
        return new SeatRemoval(removed.uuid().toString(), lobbyViews(id));
    }

    /** Host-only: frees a bot seat of a not-yet-started room (see {@link SessionGame#removeBot}). */
    public synchronized void removeBot(GameId id, String hostToken, int seat) {
        SessionGame lobby = openLobbyOwnedBy(id, hostToken);
        lobby.removeBot(new PlayerId(seat));
        lobby.bumpVersion();
    }

    /**
     * Host-only: merges {@code changes} into the options of a not-yet-started room. The whole merged map is
     * validated by the game (an unknown key or value is refused and nothing changes), then stored with the
     * defaults filled. Returns the per-viewer lobby views, computed in the same critical section.
     */
    public synchronized List<AddressedLobbyView> updateOptions(GameId id, String hostToken, Map<String, String> changes) {
        SessionGame lobby = openLobbyOwnedBy(id, hostToken);
        Map<String, String> merged = new HashMap<>(lobby.options().values());
        merged.putAll(changes);
        GameOptions validated = gameFactories.validate(lobby.gameType(), GameOptions.of(merged));
        repository.saveLobby(new SessionGame(lobby.id(), lobby.gameType(), validated, lobby.players(), lobby.version()));
        lobby.bumpVersion();
        return lobbyViews(id);
    }

    /**
     * Occupant-only: renames the caller's own seat in a not-yet-started room (see {@link SessionGame#renameSeat}).
     * Returns the per-viewer lobby views, computed in the same critical section.
     */
    public synchronized List<AddressedLobbyView> renameSeat(GameId id, String token, String name) {
        if (repository.findGame(id).isPresent()) {
            throw new GameAlreadyStartedException(id);
        }
        SessionGame lobby = repository.loadSessionGame(id);
        PlayerId actor = lobby.findPlayerByToken(new SessionToken(token)).orElseThrow(InvalidTokenException::new);
        lobby.renameSeat(actor, name);
        lobby.bumpVersion();
        return lobbyViews(id);
    }

    /**
     * Host-only: sets one option on a computer seat of a not-yet-started room. The game validates the key and
     * the value (strictly); a refused update leaves the seat unchanged. Returns the per-viewer lobby views.
     */
    public synchronized List<AddressedLobbyView> setSeatOption(GameId id, String hostToken, int seat, String key, String value) {
        SessionGame lobby = openLobbyOwnedBy(id, hostToken);
        gameFactories.validateSeatOption(lobby.gameType(), key, value);
        lobby.setSeatOption(new PlayerId(seat), key, value);
        lobby.bumpVersion();
        return lobbyViews(id);
    }

    /** The options set on a seat (empty for a human or a free seat); answers the {@code BotSeatOptions} port. */
    public synchronized Map<String, String> seatOptions(GameId id, PlayerId seat) {
        SessionPlayer player = repository.loadSessionGame(id).players().get(seat);
        return player == null ? Map.of() : player.options();
    }

    /** Seats held by computer players, for whoever must drive them once the game runs. */
    public synchronized Set<PlayerId> botSeats(GameId id) {
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

    /**
     * Called with the id of a lobby removed because its last human left. Wired to the eviction listeners
     * (presence registry, forfeit log, bot coordinators) so a removed lobby leaves no per-game state behind.
     */
    public void setLobbyRemovalListener(Consumer<GameId> listener) {
        this.lobbyRemovalListener = listener;
    }

    /**
     * A human leaves a not-yet-started lobby; the seats behind them shift (see {@link SessionGame#vacate}) and the
     * next human becomes the host if the host left. The lobby is removed only when no human remains. The seat is
     * resolved from the token inside the lock, so of two simultaneous departures the second sees the shifted seats.
     */
    public LeaveResult leaveLobby(GameId id, String token) {
        LeaveResult result = leaveUnderLock(id, token);
        if (result.lobbyRemoved()) {
            lobbyRemovalListener.accept(id);
        }
        return result;
    }

    private synchronized LeaveResult leaveUnderLock(GameId id, String token) {
        if (repository.findGame(id).isPresent()) {
            throw new GameAlreadyStartedException(id);
        }
        SessionGame lobby = repository.loadSessionGame(id);
        PlayerId leaver = lobby.findPlayerByToken(new SessionToken(token)).orElseThrow(InvalidTokenException::new);
        lobby.vacate(leaver);
        if (lobby.players().values().stream().noneMatch(seat -> seat.isClaimed() && !seat.isBot())) {
            repository.remove(id);
            return new LeaveResult(true, List.of());
        }
        lobby.bumpVersion();
        return new LeaveResult(false, lobbyViews(id));
    }

    public String gameType(GameId id) {
        return repository.loadSessionGame(id).gameType();
    }

    public synchronized Game startGame(GameId id, String hostToken) {
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

    /** Starts the room's game and returns it as the game type the caller has checked the room for. */
    public synchronized <T extends Game> T startGame(GameId id, String hostToken, Class<T> type) {
        return type.cast(startGame(id, hostToken));
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
    public Optional<Game> leaveRematch(GameId id, PlayerId playerId) {
        // Lock order: the game's own lock is never taken under this monitor (a Kobo action holds its game lock and
        // then calls the service), so the game is inspected before entering the monitor. A finished game stays finished.
        if (repository.findGame(id).filter(Game::isFinished).isEmpty()) {
            return Optional.empty();
        }
        return leaveRematchUnderLock(id, playerId);
    }

    private synchronized Optional<Game> leaveRematchUnderLock(GameId id, PlayerId playerId) {
        SessionGame session = repository.loadSessionGame(id);
        session.leaveRematch(playerId);
        return isRematchReady(session) ? Optional.of(rematch(id)) : Optional.empty();
    }

    private boolean isRematchReady(SessionGame session) {
        return session.isRematchUnanimous()
                && session.isRematchPlayable(gameFactories.minPlayers(session.gameType()));
    }

    public synchronized Game rematch(GameId id) {
        SessionGame session = repository.loadSessionGame(id);
        session.releaseDepartedSeats();
        // Deal the rematch to the players who actually joined, not every room seat (matches startGame).
        Game fresh = gameFactories.factoryFor(session.gameType()).create(id, session.claimedCount(), session.options());
        session.clearRematch();
        repository.save(fresh, session);
        return fresh;
    }

    public synchronized List<SeatView> seats(GameId gameId) {
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

    public synchronized boolean isSeatClaimed(GameId gameId, PlayerId playerId) {
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

    public synchronized String tokenForSeat(GameId gameId, PlayerId playerId) {
        return repository.loadSessionToken(gameId, playerId).uuid().toString();
    }

    @Override
    public synchronized Optional<PlayerId> findPlayerIdByToken(GameId gameId, String token) {
        return repository.loadSessionGame(gameId).findPlayerByToken(new SessionToken(token));
    }

    public synchronized LobbyView lobbyView(GameId id, String token) {
        SessionGame lobby = repository.loadSessionGame(id);
        PlayerId viewer = lobby.findPlayerByToken(new SessionToken(token))
                .orElseThrow(InvalidTokenException::new);
        return LobbyView.forViewer(lobby, minPlayers(lobby.gameType()), maxPlayers(lobby.gameType()),
                gameFactories.optionDescriptors(lobby.gameType()),
                gameFactories.seatOptionDescriptors(lobby.gameType()), viewer);
    }

    public synchronized List<AddressedLobbyView> lobbyViews(GameId id) {
        SessionGame lobby = repository.loadSessionGame(id);
        int min = minPlayers(lobby.gameType());
        int max = maxPlayers(lobby.gameType());
        List<OptionDescriptor> descriptors = gameFactories.optionDescriptors(lobby.gameType());
        List<OptionDescriptor> seatDescriptors = gameFactories.seatOptionDescriptors(lobby.gameType());
        return lobby.seats().stream()
                .filter(seat -> seat.isClaimed() && !seat.isBot())
                .map(seat -> new AddressedLobbyView(seat.token().uuid().toString(),
                        LobbyView.forViewer(lobby, min, max, descriptors, seatDescriptors, seat.id())))
                .toList();
    }
}
