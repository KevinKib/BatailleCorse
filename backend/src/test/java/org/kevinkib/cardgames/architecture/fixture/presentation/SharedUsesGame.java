package org.kevinkib.cardgames.architecture.fixture.presentation;

import org.kevinkib.cardgames.architecture.fixture.kobo.presentation.PresentationUsesOtherGame;

/** Violates: the shared presentation must not depend on a concrete game. */
public class SharedUsesGame {
    PresentationUsesOtherGame game;
}
