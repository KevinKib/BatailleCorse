package org.kevinkib.cardgames.architecture.fixture.kobo.domain;

import org.kevinkib.cardgames.architecture.fixture.threemenmorris.domain.CycleB;

/** Violates: top-level packages must be free of cycles. */
public class CycleA {
    CycleB b;
}
