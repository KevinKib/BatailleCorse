package org.kevinkib.cardgames.architecture.fixture.kobo.presentation;

import org.kevinkib.cardgames.architecture.fixture.bullshit.domain.Marker;

/** Violates: a game must not depend on another game. */
public class PresentationUsesOtherGame {
    Marker marker;
}
