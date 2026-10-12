package org.kevinkib.cardgames.architecture.fixture.bullshit.domain;

import org.kevinkib.cardgames.architecture.fixture.sessionmanagement.SessionUsesGame;

/** Violates: a game domain must not know sessionmanagement. */
public class DomainUsesSession {
    SessionUsesGame session;
}
