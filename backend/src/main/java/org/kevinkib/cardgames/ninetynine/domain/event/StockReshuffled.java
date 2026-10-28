package org.kevinkib.cardgames.ninetynine.domain.event;

/** The stock ran out: the pile except its top card became the new stock of {@code size} cards. */
public record StockReshuffled(int size) implements NinetyNineEvent {
}
