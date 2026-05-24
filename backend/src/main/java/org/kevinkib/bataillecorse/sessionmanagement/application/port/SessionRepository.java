package org.kevinkib.bataillecorse.sessionmanagement.application.port;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionGame;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;

public interface SessionRepository {

    void save(BatailleCorse batailleCorse, SessionGame sessionGame);

    BatailleCorse load(BatailleCorseId id);

    SessionToken loadSessionToken(BatailleCorseId batailleCorseId, PlayerId playerId);

    SessionGame loadSessionGame(BatailleCorseId id);
}
