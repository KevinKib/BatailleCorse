package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.core.application.port.UsageStatistics;
import org.kevinkib.cardgames.sessionmanagement.core.domain.LobbyVisibility;
import org.kevinkib.cardgames.sessionmanagement.core.domain.NoFreeSeatException;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionPlayer;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionToken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Session use cases. Every operation that reads or mutates a lobby's seats or options, and the computation of the
 * per-viewer views with their tokens, runs under the monitor of this service (one lock for all lobbies: traffic is
 * tiny, and a per-game lock can replace it later without changing a caller). Each mutation of a lobby increments
 * its version, carried by every view, so a client can drop a view older than the one it holds.
 */
public class SessionService implements GameDirectory {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository repository;
    private final GameFactories gameFactories;
    private final UsageStatistics statistics;
    /** Started games whose end was already reported; pruned to the games still held. Guarded by this service's lock. */
    private final Set<GameId> reportedFinished = new HashSet<>();
    private volatile Consumer<GameId> lobbyRemovalListener = removed -> { };
    private volatile Consumer<String> publicLobbiesListener = gameType -> { };
    /** Game types whose public list changed during the running use case. Guarded by this service's lock. */
    private final Set<String> changedTypes = new LinkedHashSet<>();
    /** Per game type, how many times its public list changed: the version of the list. Guarded by the lock. */
    private final Map<String, Long> listVersions = new HashMap<>();

    public SessionService(SessionRepository repository, GameFactories gameFactories) {
        this(repository, gameFactories, UsageStatistics.NONE);
    }

    public SessionService(SessionRepository repository, GameFactories gameFactories, UsageStatistics statistics) {
        this.repository = repository;
        this.gameFactories = gameFactories;
        this.statistics = statistics;
    }

    public JoinResult joinRoom(GameId id, String name) {
        return notifying(() -> {
            JoinResult joined = join(id, lobby -> lobby.claimNextFreeSeat(name));
            statistics.seatJoined(repository.loadSessionGame(id).gameType());
            return joined;
        });
    }

    /**
     * Reports, once each, the started games that have ended since the last call (a win or a forfeit has no single
     * hook, so the cleanup sweep polls). A game is also reported when it is reopened for a rematch.
     */
    public synchronized void recordFinishedGames() {
        Set<GameId> held = new HashSet<>();
        for (Game game : repository.games()) {
            held.add(game.getId());
            reportFinished(game);
        }
        reportedFinished.retainAll(held);
    }

    private void reportFinished(Game game) {
        if (game.isFinished() && reportedFinished.add(game.getId())) {
            statistics.gameFinished(repository.loadSessionGame(game.getId()).gameType());
        }
    }

    private JoinResult join(GameId id, Function<SessionGame, SessionPlayer> claim) {
        if (repository.findGame(id).isPresent()) {
            throw new GameAlreadyStartedException(id);
        }
        SessionGame lobby = repository.loadSessionGame(id);
        try {
            SessionPlayer claimed = claim.apply(lobby);
            lobby.bumpVersion();
            changed(lobby.gameType());
            return new JoinResult(claimed.id(), claimed.token().uuid().toString(), lobbyViews(id));
        } catch (NoFreeSeatException e) {
            throw new RoomFullException(id);
        }
    }

    /**
     * Re-join the room for a rematch. If a game is present (finished or not), the room is first
     * reopened — the game is dropped and the lobby reset, keeping the computer players (order, names and seat
     * options) on the seats right after seat 0 — then a seat is claimed right after the humans already back, the bots
     * shifting up (so they stay removable by the host); the first caller takes seat 0 and becomes host. Synchronized so a burst of simultaneous
     * Play-Again calls reopen exactly once before any of them claims a seat.
     */
    public JoinResult playAgain(GameId id, String name) {
        return notifying(() -> playAgainUnderLock(id, name));
    }

    private JoinResult playAgainUnderLock(GameId id, String name) {
        SessionGame existing = repository.loadSessionGame(id);
        String type = existing.gameType();
        GameOptions options = existing.options();
        Optional<Game> running = repository.findGame(id);
        if (running.isPresent()) {
            reportFinished(running.get());
            reportedFinished.remove(id);
            statistics.rematch(type);
            SessionGame fresh = SessionGame.create(id, gameFactories.maxPlayers(type), type, options);
            fresh.claimBotsOf(existing);
            // The reopened lobby continues the version of the one it replaces (a client never sees it go backwards)
            // and keeps its visibility.
            repository.reopen(new SessionGame(id, type, options, fresh.players(), existing.version(),
                    existing.visibilityHolder()));
        }
        return join(id, lobby -> lobby.claimSeatAheadOfBots(name));
    }

    public RoomCreated createRoom(String gameType, String hostName) {
        return createRoom(gameType, hostName, GameOptions.none());
    }

    public RoomCreated createRoom(String gameType, String hostName, GameOptions options) {
        return createRoom(gameType, hostName, options, false);
    }

    /**
     * Creates a room, hidden from the public game list from the start when {@code privateGame}: it is never
     * public, not even for a moment.
     */
    public RoomCreated createRoom(String gameType, String hostName, GameOptions options, boolean privateGame) {
        return notifying(() -> {
            GameId id = GameId.generate();
            SessionGame lobby = SessionGame.create(id, gameFactories.maxPlayers(gameType), gameType, options);
            lobby.claimHost(hostName);
            if (privateGame) {
                lobby.setVisibility(LobbyVisibility.PRIVATE);
            }
            repository.saveLobby(lobby);
            statistics.gameCreated(gameType);
            changed(gameType);
            SessionToken hostToken = lobby.findTokenByPlayer(new PlayerId(0))
                    .orElseThrow(() -> new IllegalStateException("Host seat has no token"));
            return new RoomCreated(id.uuid().toString(), hostToken.uuid().toString());
        });
    }

    /** Host-only: fills the lowest free seat of a not-yet-started room with a computer player. */
    public PlayerId addBot(GameId id, String hostToken) {
        return notifying(() -> addBotUnderLock(id, hostToken));
    }

    private PlayerId addBotUnderLock(GameId id, String hostToken) {
        SessionGame lobby = openLobbyOwnedBy(id, hostToken);
        if (!gameFactories.supportsBots(lobby.gameType())) {
            throw new BotsNotSupportedException(id, lobby.gameType());
        }
        try {
            PlayerId bot = lobby.claimBot(null).id();
            lobby.bumpVersion();
            statistics.botAdded(lobby.gameType());
            changed(lobby.gameType());
            return bot;
        } catch (NoFreeSeatException e) {
            throw new RoomFullException(id);
        }
    }

    /** {@link #addBot} and the resulting views, in one critical section. */
    public List<AddressedLobbyView> addBotViews(GameId id, String hostToken) {
        return notifying(() -> {
            addBotUnderLock(id, hostToken);
            return lobbyViews(id);
        });
    }

    /** {@link #removeBot} and the resulting views, in one critical section. */
    public List<AddressedLobbyView> removeBotViews(GameId id, String hostToken, int seat) {
        return notifying(() -> {
            removeBotUnderLock(id, hostToken, seat);
            return lobbyViews(id);
        });
    }

    /**
     * Host-only: removes another human from a not-yet-started room, connected or not (see
     * {@link SessionGame#removeHuman}). Returns the removed player's token and the remaining humans' views.
     */
    public SeatRemoval removeSeat(GameId id, String hostToken, int seat) {
        return notifying(() -> {
            SessionGame lobby = openLobbyOwnedBy(id, hostToken);
            SessionToken removed = lobby.removeHuman(new PlayerId(seat));
            lobby.bumpVersion();
            changed(lobby.gameType());
            return new SeatRemoval(removed.uuid().toString(), lobbyViews(id));
        });
    }

    /** Host-only: frees a bot seat of a not-yet-started room (see {@link SessionGame#removeBot}). */
    public void removeBot(GameId id, String hostToken, int seat) {
        notifying(() -> {
            removeBotUnderLock(id, hostToken, seat);
            return null;
        });
    }

    private void removeBotUnderLock(GameId id, String hostToken, int seat) {
        SessionGame lobby = openLobbyOwnedBy(id, hostToken);
        lobby.removeBot(new PlayerId(seat));
        lobby.bumpVersion();
        changed(lobby.gameType());
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
        repository.saveLobby(new SessionGame(lobby.id(), lobby.gameType(), validated, lobby.players(), lobby.version(),
                lobby.visibilityHolder()));
        lobby.bumpVersion();
        return lobbyViews(id);
    }

    /**
     * Occupant-only: renames the caller's own seat in a not-yet-started room (see {@link SessionGame#renameSeat}).
     * Returns the per-viewer lobby views, computed in the same critical section.
     */
    public List<AddressedLobbyView> renameSeat(GameId id, String token, String name) {
        return notifying(() -> {
            if (repository.findGame(id).isPresent()) {
                throw new GameAlreadyStartedException(id);
            }
            SessionGame lobby = repository.loadSessionGame(id);
            PlayerId actor = lobby.findPlayerByToken(new SessionToken(token)).orElseThrow(InvalidTokenException::new);
            lobby.renameSeat(actor, name);
            lobby.bumpVersion();
            changed(lobby.gameType());
            return lobbyViews(id);
        });
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
        LeaveResult result = notifying(() -> leaveUnderLock(id, token));
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
            changed(lobby.gameType());
            return new LeaveResult(true, List.of());
        }
        lobby.bumpVersion();
        changed(lobby.gameType());
        return new LeaveResult(false, lobbyViews(id));
    }

    /**
     * Records that the human holding {@code token} lost or regained its connection in a not-yet-started lobby.
     * Returns the views to publish; empty when nothing changed (same state, unknown token, or the game started).
     */
    @Override
    public List<AddressedLobbyView> markSeatConnection(GameId id, String token, boolean connected) {
        return notifying(() -> {
            if (repository.findGame(id).isPresent()) {
                return List.<AddressedLobbyView>of();
            }
            SessionGame lobby;
            try {
                lobby = repository.loadSessionGame(id);
            } catch (IllegalArgumentException unknownLobby) {
                return List.<AddressedLobbyView>of();
            }
            if (!lobby.markConnection(new SessionToken(token), connected)) {
                return List.<AddressedLobbyView>of();
            }
            lobby.bumpVersion();
            changed(lobby.gameType());
            return lobbyViews(id);
        });
    }

    public String gameType(GameId id) {
        return repository.loadSessionGame(id).gameType();
    }

    public Game startGame(GameId id, String hostToken) {
        return notifying(() -> startGameUnderLock(id, hostToken));
    }

    private Game startGameUnderLock(GameId id, String hostToken) {
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
        int bots = lobby.botSeats().size();
        statistics.gameStarted(lobby.gameType(), claimed - bots, bots);
        changed(lobby.gameType());
        return game;
    }

    /** Starts the room's game and returns it as the game type the caller has checked the room for. */
    public <T extends Game> T startGame(GameId id, String hostToken, Class<T> type) {
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

    /**
     * The public game list of a game type, as it is now. Its version grows with every change of a list of that
     * type, so a client can drop a list older than the one it holds. An unknown game type is refused.
     */
    public synchronized PublicLobbyList publicLobbies(String gameType) {
        gameFactories.maxPlayers(gameType);
        List<PublicLobbies.Entry> entries = repository.sessionsOfType(gameType).stream()
                .map(session -> new PublicLobbies.Entry(session, repository.findGame(session.id()).isPresent(),
                        repository.openedAt(session.id())))
                .toList();
        return PublicLobbies.list(gameType, entries, PublicLobbies.MAX_LISTED, listVersions.getOrDefault(gameType, 0L));
    }

    /** Host-only: hides the lobby from the public game list, or lists it again. */
    public List<AddressedLobbyView> setPrivate(GameId id, String hostToken, boolean privateGame) {
        return notifying(() -> {
            SessionGame lobby = openLobbyOwnedBy(id, hostToken);
            lobby.setVisibility(privateGame ? LobbyVisibility.PRIVATE : LobbyVisibility.PUBLIC);
            lobby.bumpVersion();
            changed(lobby.gameType());
            return lobbyViews(id);
        });
    }

    /**
     * Called with the game type after every change of what its public list shows, once the monitor is released.
     * It is not called for options, seat options or an unchanged connection mark.
     */
    public void setPublicLobbiesListener(Consumer<String> listener) {
        this.publicLobbiesListener = listener;
    }

    /** Tells the listener that every public list changed (a room was evicted: its game type is no longer known). */
    public void publicListsChanged() {
        notifying(() -> {
            gameFactories.gameTypes().forEach(this::changed);
            return null;
        });
    }

    /** Records, under the lock, that a public list changed; the listener hears of it when the use case ends. */
    private void changed(String gameType) {
        listVersions.merge(gameType, 1L, Long::sum);
        changedTypes.add(gameType);
    }

    /**
     * Runs a use case under the monitor, then tells the listener, once per game type whose list changed, after the
     * monitor is released (the listener may take other locks or call back into this service).
     */
    private <T> T notifying(Supplier<T> useCase) {
        List<String> types = new ArrayList<>();
        try {
            synchronized (this) {
                try {
                    return useCase.get();
                } finally {
                    types.addAll(changedTypes);
                    changedTypes.clear();
                }
            }
        } finally {
            for (String type : types) {
                try {
                    publicLobbiesListener.accept(type);
                } catch (RuntimeException e) {
                    log.warn("Public lobbies listener failed for {}", type, e);
                }
            }
        }
    }
}
