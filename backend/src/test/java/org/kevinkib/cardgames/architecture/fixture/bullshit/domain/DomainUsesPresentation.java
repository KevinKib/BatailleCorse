package org.kevinkib.cardgames.architecture.fixture.bullshit.domain;

import org.kevinkib.cardgames.architecture.fixture.bullshit.presentation.GamePresentation;

/** Violates: a game domain must not know its own presentation. */
public class DomainUsesPresentation {
    GamePresentation presentation;
}
