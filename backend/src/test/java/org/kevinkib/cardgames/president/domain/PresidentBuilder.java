package org.kevinkib.cardgames.president.domain;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.president.domain.rules.AfterPass;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.List;

import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/** Builds a President round from explicit hands (one per call) and a leader, for rule scenarios. */
public final class PresidentBuilder {

    private final List<List<Card>> hands = new ArrayList<>();
    private PresidentOptions options = PresidentOptions.defaults();
    private int leader;
    private GameId id;

    public static PresidentBuilder aPresident() {
        return new PresidentBuilder();
    }

    /** One hand per call, seat 0 first, e.g. {@code withHand("3C 3H 2S")}. */
    public PresidentBuilder withHand(String codes) {
        hands.add(cards(codes));
        return this;
    }

    public PresidentBuilder leader(int seat) {
        this.leader = seat;
        return this;
    }

    public PresidentBuilder afterPass(AfterPass afterPass) {
        this.options = options.withAfterPass(afterPass);
        return this;
    }

    public PresidentBuilder options(PresidentOptions options) {
        this.options = options;
        return this;
    }

    public PresidentBuilder withId(GameId id) {
        this.id = id;
        return this;
    }

    public President build() {
        return new President(id == null ? GameId.generate() : id, options, 1L, hands, leader);
    }
}
