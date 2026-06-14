package org.kevinkib.cardgames.bullshit.domain;

public sealed interface ClaimTarget permits RankTarget {
    String label();
}
