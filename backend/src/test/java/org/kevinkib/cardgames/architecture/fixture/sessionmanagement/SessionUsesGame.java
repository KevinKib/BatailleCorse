package org.kevinkib.cardgames.architecture.fixture.sessionmanagement;

import org.kevinkib.cardgames.architecture.fixture.bullshit.domain.Marker;

/** Violates: sessionmanagement must not know a concrete game. */
public class SessionUsesGame {
    Marker marker;
}
