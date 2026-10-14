package org.kevinkib.cardgames.kobo.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.event.PrivateReveal;
import org.kevinkib.cardgames.kobo.domain.tableau.SlotRef;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.Rank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * What one bot seat has learned about face-down cards: {@code (slot, revision) -> card}. An entry is only ever
 * created from a card the seat actually saw, relocated by an exact public move, or dropped, so the memory is
 * never wrong, only incomplete. A slot whose revision no longer matches the entry is forgotten (the card there
 * changed in a way the bot cannot follow).
 *
 * <p>Fed, on every broadcast and in this order, by the public moves ({@link #onMoved}, {@link #onSwapped},
 * {@link #onOwnDrawnPlaced}) and then by the seat's view ({@link #observe}). Not thread-safe: the owner calls it
 * under the game's monitor.
 */
public final class KoboBotMemory {

    /** A remembered card at a slot, valid while the slot still has this revision. */
    public record Known(SlotRef ref, Card card, int revision) {
    }

    private static final class Entry {
        Card card;
        int revision;
        /** The card was moved here by a public move: its revision is the destination's next one, set by observe. */
        boolean restamp;

        Entry(Card card, int revision, boolean restamp) {
            this.card = card;
            this.revision = revision;
            this.restamp = restamp;
        }
    }

    private final PlayerId seat;
    private final boolean followsMoves;
    private final Map<SlotRef, Entry> entries = new LinkedHashMap<>();
    private Card held;
    private int round = 1;
    private int ownTurns;

    public KoboBotMemory(PlayerId seat, boolean followsMoves) {
        this.seat = seat;
        this.followsMoves = followsMoves;
    }

    public PlayerId seat() {
        return seat;
    }

    /** Own turns ended in the current round. */
    public int ownTurns() {
        return ownTurns;
    }

    // ------------------------------------------------------------------ inputs

    /**
     * The card at {@code from} was moved to {@code to} (a match gift): {@code to} now holds it, whatever it held
     * before is gone, {@code from} is empty. Ignored when the level does not follow public moves.
     */
    public void onMoved(SlotRef from, SlotRef to) {
        if (!followsMoves) {
            return;
        }
        Entry moved = entries.remove(from);
        entries.remove(to);
        if (moved != null) {
            entries.put(to, new Entry(moved.card, moved.revision, true));
        }
    }

    /** The cards at {@code a} and {@code b} traded places (a blind swap or a king swap). */
    public void onSwapped(SlotRef a, SlotRef b) {
        if (!followsMoves) {
            return;
        }
        Entry atA = entries.remove(a);
        Entry atB = entries.remove(b);
        if (atA != null) {
            entries.put(b, new Entry(atA.card, atA.revision, true));
        }
        if (atB != null) {
            entries.put(a, new Entry(atB.card, atB.revision, true));
        }
    }

    /** The seat's own swap: the card it was holding is now in {@code slot}. Always followed (it is its own act). */
    public void onOwnDrawnPlaced(int slot) {
        SlotRef ref = new SlotRef(seat, slot);
        entries.remove(ref);
        if (held != null) {
            entries.put(ref, new Entry(held, 0, true));
        }
        held = null;
    }

    /**
     * Brings the memory in line with the seat's current view: re-stamps relocated entries, drops those whose slot
     * changed, records the private parts, and starts afresh when a new round was dealt (revisions restart at 0).
     */
    public void observe(KoboBotView view) {
        if (view.round() != round) {
            entries.clear();
            held = null;
            ownTurns = 0;
            round = view.round();
        }
        for (Iterator<Map.Entry<SlotRef, Entry>> it = entries.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<SlotRef, Entry> mapEntry = it.next();
            Optional<KoboBotView.SlotView> slot = slotOf(view, mapEntry.getKey());
            Entry entry = mapEntry.getValue();
            if (slot.isEmpty() || !slot.get().occupied()) {
                it.remove();
            } else if (entry.restamp) {
                entry.revision = slot.get().revision();
                entry.restamp = false;
            } else if (entry.revision != slot.get().revision()) {
                it.remove();
            }
        }
        view.startingCards().forEach((slot, card) -> {
            SlotRef ref = new SlotRef(seat, slot);
            slotOf(view, ref).ifPresent(s -> entries.put(ref, new Entry(card, s.revision(), false)));
        });
        view.reveal().ifPresent(reveal -> recordReveal(view, reveal));
        held = view.drawn().orElse(null);
    }

    /** A peek stays in the view until the seat's next command; it is only valid while the slot is unchanged. */
    private void recordReveal(KoboBotView view, PrivateReveal reveal) {
        slotOf(view, reveal.ref())
                .filter(slot -> slot.occupied() && slot.revision() == reveal.revision())
                .ifPresent(slot -> entries.put(reveal.ref(), new Entry(reveal.card(), reveal.revision(), false)));
    }

    private static Optional<KoboBotView.SlotView> slotOf(KoboBotView view, SlotRef ref) {
        List<KoboBotView.SlotView> slots = view.tableaux().get(ref.seat());
        if (slots == null || ref.slot() < 0 || ref.slot() >= slots.size()) {
            return Optional.empty();
        }
        return Optional.of(slots.get(ref.slot()));
    }

    /** Drops each entry with the given probability, one draw per entry (none when it is 0). */
    public void forget(RandomGenerator random, double probability) {
        if (probability <= 0) {
            return;
        }
        entries.values().removeIf(entry -> random.nextDouble() < probability);
    }

    /** The seat ended a turn (END_TURN or KOBO): counts it and forgets. */
    public void onOwnTurnEnded(RandomGenerator random, double forgetProbability) {
        ownTurns++;
        forget(random, forgetProbability);
    }

    /** Records a card seen at a slot revision. Package-private: only the memory's own inputs and tests use it. */
    void learn(SlotRef ref, Card card, int revision) {
        entries.put(ref, new Entry(card, revision, false));
    }

    // ----------------------------------------------------------------- queries

    public Optional<Known> known(SlotRef ref) {
        Entry entry = entries.get(ref);
        return entry == null ? Optional.empty() : Optional.of(new Known(ref, entry.card, entry.revision));
    }

    /** Remembered cards of this rank: the seat's own first, then the others', each in seat and slot order. */
    public List<Known> knownOfRank(Rank rank) {
        List<Known> found = new ArrayList<>();
        entries.forEach((ref, entry) -> {
            if (entry.card.getRank().equals(rank)) {
                found.add(new Known(ref, entry.card, entry.revision));
            }
        });
        found.sort(Comparator.comparing((Known k) -> !k.ref().seat().equals(seat))
                .thenComparingInt(k -> k.ref().seat().id()).thenComparingInt(k -> k.ref().slot()));
        return found;
    }

    /** Remembered cards of one seat, in slot order. */
    public List<Known> knownSlots(PlayerId owner) {
        List<Known> found = new ArrayList<>();
        entries.forEach((ref, entry) -> {
            if (ref.seat().equals(owner)) {
                found.add(new Known(ref, entry.card, entry.revision));
            }
        });
        found.sort(Comparator.comparingInt(k -> k.ref().slot()));
        return found;
    }
}
