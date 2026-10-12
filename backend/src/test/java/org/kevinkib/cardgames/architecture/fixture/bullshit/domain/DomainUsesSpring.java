package org.kevinkib.cardgames.architecture.fixture.bullshit.domain;

import org.springframework.stereotype.Service;

/** Violates: a game domain must not depend on Spring. */
@Service
public class DomainUsesSpring {}
