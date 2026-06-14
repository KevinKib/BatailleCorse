package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cards.domain.Card;

import java.util.List;

public interface ClaimMode {

    ClaimTarget initial();

    ClaimTarget next(ClaimTarget current);

    boolean matches(List<Card> cards, ClaimTarget target);
}
