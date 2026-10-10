package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's face-down cards, as slots with stable positions. A removed card leaves an empty slot
 * that is never renumbered; every change of the card in a slot bumps that slot's revision, which is
 * how clients know that what they remembered about it is no longer valid.
 */
public class Tableau {

    /** One position; {@code card} is null when the slot is empty. */
    public record Slot(int index, Card card, int revision) {
        public boolean occupied() {
            return card != null;
        }
    }

    private final List<Card> cards;
    private final List<Integer> revisions = new ArrayList<>();

    public Tableau(List<Card> initial) {
        this.cards = new ArrayList<>(initial);
        initial.forEach(c -> revisions.add(0));
    }

    public int size() {
        return cards.size();
    }

    public boolean isOccupied(int slot) {
        return slot >= 0 && slot < cards.size() && cards.get(slot) != null;
    }

    public int revision(int slot) throws InvalidSlotException {
        checkIndex(slot);
        return revisions.get(slot);
    }

    public Card card(int slot) throws InvalidSlotException, EmptySlotException {
        checkIndex(slot);
        Card card = cards.get(slot);
        if (card == null) {
            throw new EmptySlotException("Slot " + slot + " is empty");
        }
        return card;
    }

    /** Puts a card in an occupied slot and returns the previous card. */
    public Card replace(int slot, Card card) throws InvalidSlotException, EmptySlotException {
        Card old = card(slot);
        cards.set(slot, card);
        bump(slot);
        return old;
    }

    public Card remove(int slot) throws InvalidSlotException, EmptySlotException {
        Card old = card(slot);
        cards.set(slot, null);
        bump(slot);
        return old;
    }

    /** Fills an empty slot (a card given by another player). */
    public void put(int slot, Card card) throws InvalidSlotException {
        checkIndex(slot);
        cards.set(slot, card);
        bump(slot);
    }

    /** Adds a card in a new slot at the end; empty slots are never reused. */
    public int append(Card card) {
        cards.add(card);
        revisions.add(0);
        return cards.size() - 1;
    }

    public int handSize() {
        return (int) cards.stream().filter(c -> c != null).count();
    }

    public int points() {
        return cards.stream().filter(c -> c != null).mapToInt(CardPoints::of).sum();
    }

    public List<Card> cards() {
        return cards.stream().filter(c -> c != null).toList();
    }

    public List<Integer> occupiedSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i) != null) {
                slots.add(i);
            }
        }
        return slots;
    }

    public List<Slot> slots() {
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            slots.add(new Slot(i, cards.get(i), revisions.get(i)));
        }
        return slots;
    }

    private void bump(int slot) {
        revisions.set(slot, revisions.get(slot) + 1);
    }

    private void checkIndex(int slot) throws InvalidSlotException {
        if (slot < 0 || slot >= cards.size()) {
            throw new InvalidSlotException("Unknown slot " + slot);
        }
    }
}
