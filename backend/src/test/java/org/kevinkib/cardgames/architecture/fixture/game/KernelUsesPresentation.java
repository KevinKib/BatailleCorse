package org.kevinkib.cardgames.architecture.fixture.game;

import org.kevinkib.cardgames.architecture.fixture.presentation.SharedPresentation;

/** Violates: the shared kernel must not know the shared presentation. */
public class KernelUsesPresentation {
    SharedPresentation presentation;
}
