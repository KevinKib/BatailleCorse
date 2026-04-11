package org.kevinkib.bataillecorse.sessionmanagement.application.port;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;

public interface SessionRepository {

    void save(BatailleCorse batailleCorse);

}
