package org.kevinkib.cardgames.bullshit.domain.claim;

public sealed interface ClaimTarget permits RankTarget, SuitTarget {
    String label();
}
