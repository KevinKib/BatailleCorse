package org.kevinkib.bataillecorse.sessionmanagement.application;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.Player;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.bataillecorse.sessionmanagement.domain.GameMode;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionGame;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;

import java.util.Optional;

public class SessionService {

    private static final PlayerId JOINER_SEAT = new PlayerId(1);

    private final SessionRepository repository;

    public SessionService(SessionRepository repository) {
        this.repository = repository;
    }

    public BatailleCorse createGame(int nbPlayers) {
        return createGame(nbPlayers, GameMode.SOLO);
    }

    public BatailleCorse createGame(int nbPlayers, GameMode mode) {
        BatailleCorseId id = BatailleCorseId.generate();
        BatailleCorse batailleCorse = new BatailleCorse(id, nbPlayers);

        SessionGame sessionGame = SessionGame.create(id, batailleCorse.getPlayers());

        if (mode == GameMode.SOLO) {
            for (Player player : batailleCorse.getPlayers()) {
                sessionGame.claim(player.id());
            }
        } else {
            sessionGame.claim(new PlayerId(0));
        }

        repository.save(batailleCorse, sessionGame);

        return batailleCorse;
    }

    public JoinResult joinGame(BatailleCorseId gameId) {
        SessionGame sessionGame = repository.loadSessionGame(gameId);

        if (sessionGame.isClaimed(JOINER_SEAT)) {
            throw new SeatUnavailableException(JOINER_SEAT);
        }

        sessionGame.claim(JOINER_SEAT);
        SessionToken token = sessionGame.findTokenByPlayer(JOINER_SEAT)
                .orElseThrow(() -> new IllegalStateException("Seat " + JOINER_SEAT.id() + " has no token"));

        return new JoinResult(JOINER_SEAT, token);
    }

    public boolean isSeatClaimed(BatailleCorseId gameId, PlayerId playerId) {
        return repository.loadSessionGame(gameId).isClaimed(playerId);
    }

    public BatailleCorse getGame(BatailleCorseId id) throws InvalidGameIdException {
        try {
            return repository.load(id);
        } catch (IllegalArgumentException e) {
            throw new InvalidGameIdException(id);
        }
    }

    public SessionToken loadTokenByPlayerId(BatailleCorseId batailleCorseId, PlayerId playerId) {
        return repository.loadSessionToken(batailleCorseId, playerId);
    }

    public Optional<PlayerId> findPlayerIdByToken(BatailleCorseId gameId, SessionToken token) {
        return repository.loadSessionGame(gameId).findPlayerByToken(token);
    }
}
