package org.kevinkib.cardgames.bullshit.domain.bot;

import org.kevinkib.cardgames.bullshit.domain.claim.ClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimTarget;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What one bot has learned during a game, fed from public information only: the cards it put in the
 * pile (it knows what they are) and the cards a Bullshit call revealed. Mutable, owned by one bot.
 */
public final class BotMemory {

    private static final List<Card> FULL_DECK = fullDeck();

    private final PlayerId me;
    private final List<Card> ownPileCards = new ArrayList<>();
    private final Map<PlayerId, List<Card>> revealedBySeat = new HashMap<>();

    public BotMemory(PlayerId me) {
        this.me = me;
    }

    private static List<Card> fullDeck() {
        List<Card> cards = new ArrayList<>();
        for (FrenchRank rank : FrenchRank.getRanks()) {
            for (FrenchSuit suit : FrenchSuit.getSuits()) {
                cards.add(new Card(rank, suit));
            }
        }
        return List.copyOf(cards);
    }

    /** The bot played these cards: it knows they now sit in the pile. */
    public void onOwnDiscard(List<Card> cards) {
        ownPileCards.addAll(cards);
    }

    /** Someone picked the pile up: the bot no longer knows where its cards are. */
    public void onPileTaken() {
        ownPileCards.clear();
    }

    /** A call revealed these cards and {@code picker} took them. The bot's own cards are not tracked here. */
    public void onReveal(PlayerId picker, List<Card> cards) {
        if (picker.equals(me)) {
            return;
        }
        revealedBySeat.computeIfAbsent(picker, seat -> new ArrayList<>()).addAll(cards);
    }

    /** The seat played some cards, so the bot cannot tell which of its known cards left. */
    public void onDiscardBy(PlayerId seat) {
        revealedBySeat.remove(seat);
    }

    public List<Card> knownPileCards() {
        return List.copyOf(ownPileCards);
    }

    /** Cards known to be held by {@code seat} that satisfy {@code target}. */
    public int knownMatches(PlayerId seat, ClaimTarget target, ClaimMode mode) {
        return countMatching(revealedBySeat.getOrDefault(seat, List.of()), target, mode);
    }

    /** Cards the bot cannot see: the deck minus its own hand minus the pile cards it knows. */
    public int unseenPopulation(List<Card> ownHand) {
        return unseen(ownHand).size();
    }

    /** Unseen cards that satisfy {@code target}. */
    public int unseenMatching(List<Card> ownHand, ClaimTarget target, ClaimMode mode) {
        return countMatching(unseen(ownHand), target, mode);
    }

    private List<Card> unseen(List<Card> ownHand) {
        List<Card> unseen = new ArrayList<>(FULL_DECK);
        ownHand.forEach(unseen::remove);
        ownPileCards.forEach(unseen::remove);
        return unseen;
    }

    private static int countMatching(List<Card> cards, ClaimTarget target, ClaimMode mode) {
        int count = 0;
        for (Card card : cards) {
            if (mode.matches(List.of(card), target)) {
                count++;
            }
        }
        return count;
    }
}
