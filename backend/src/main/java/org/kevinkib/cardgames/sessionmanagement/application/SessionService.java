package org.kevinkib.cardgames.sessionmanagement.application;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.bataillecorse.domain.Player;
import org.kevinkib.cardgames.bataillecorse.domain.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.cardgames.sessionmanagement.domain.GameMode;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionPlayer;
import org.kevinkib.cardgames.sessionmanagement.domain.SessionToken;

import java.util.List;
import java.util.Optional;

public class SessionService {

    private static final PlayerId JOINER_SEAT = new PlayerId(1);

    private final SessionRepository repository;

    public SessionService(SessionRepository repository) {
        this.repository = repository;
    }

    public BatailleCorse createGame(int nbPlayers) {
        return createGame(nbPlayers, GameMode.SOLO, null);
    }

    public BatailleCorse createGame(int nbPlayers, GameMode mode) {
        return createGame(nbPlayers, mode, null);
    }

    public BatailleCorse createGame(int nbPlayers, GameMode mode, String creatorName) {
        GameId id = GameId.generate();
        BatailleCorse batailleCorse = new BatailleCorse(id, nbPlayers);

        SessionGame sessionGame = SessionGame.create(id, batailleCorse.getPlayers());

        if (mode == GameMode.SOLO) {
            for (Player player : batailleCorse.getPlayers()) {
                sessionGame.claim(player.id(), defaultNameFor(player.id()));
            }
        } else {
            PlayerId creatorSeat = new PlayerId(0);
            sessionGame.claim(creatorSeat, resolveName(creatorSeat, creatorName));
        }

        repository.save(batailleCorse, sessionGame);

        return batailleCorse;
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

    public SessionGame getGameSession(GameId id) {
        return repository.loadSessionGame(id);
    }

    public BatailleCorse rematch(GameId id) {
        SessionGame session = repository.loadSessionGame(id);
        BatailleCorse fresh = new BatailleCorse(id, session.seats().size());
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

    public BatailleCorse getGame(GameId id) throws InvalidGameIdException {
        try {
            return repository.load(id);
        } catch (IllegalArgumentException e) {
            throw new InvalidGameIdException(id);
        }
    }

    public void touch(GameId id) {
        repository.touch(id);
    }

    public SessionToken loadTokenByPlayerId(GameId batailleCorseId, PlayerId playerId) {
        return repository.loadSessionToken(batailleCorseId, playerId);
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
