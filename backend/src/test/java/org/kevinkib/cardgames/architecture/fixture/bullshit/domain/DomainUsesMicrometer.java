package org.kevinkib.cardgames.architecture.fixture.bullshit.domain;

import io.micrometer.core.instrument.Counter;

/** Violates: a game domain must know nothing about metrics. */
public class DomainUsesMicrometer {

    Counter counter;
}
