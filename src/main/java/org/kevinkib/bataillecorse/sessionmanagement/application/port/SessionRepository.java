package org.kevinkib.bataillecorse.sessionmanagement.application.port;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;

public interface SessionRepository {

    void save(BatailleCorse batailleCorse);

    BatailleCorse load(BatailleCorseId id);
}
