package org.kevinkib.cardgames.bullshit.domain.bot;

import org.kevinkib.cardgames.bullshit.domain.Action;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimTarget;
import org.kevinkib.cardgames.bullshit.domain.pile.Discard;
import org.kevinkib.cardgames.bullshit.domain.player.Player;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything a bot may know at its seat: exactly what {@code BullshitDto.forViewer} shows a human
 * sitting there. It holds the seat's own cards and public facts only (hand sizes, the claim on the
 * table with its count but never the cards played), and has no accessor for another seat's cards.
 * The strategy receives this record, never the {@link Bullshit} aggregate.
 */
public record BotObservation(
        PlayerId me,
        List<Card> hand,
        Map<PlayerId, Integer> handCounts,
        ClaimTarget currentTarget,
        int pileSize,
        Optional<Claim> lastClaim,
        PlayerId currentPlayer,
        Optional<PlayerId> pendingWinner,
        List<Action> actions) {

    /** The claim on the table: who made it, what they said, how many cards, never which cards. */
    public record Claim(PlayerId claimant, ClaimTarget target, int count) {
    }

    public BotObservation {
        hand = List.copyOf(hand);
        handCounts = Map.copyOf(handCounts);
        actions = List.copyOf(actions);
    }

    public static BotObservation forSeat(Bullshit game, PlayerId seat) {
        synchronized (game) {
            List<Player> players = game.getPlayers();
            Player me = players.stream()
                    .filter(p -> p.id().equals(seat))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Seat " + seat + " is not in the game"));

            Map<PlayerId, Integer> counts = new LinkedHashMap<>();
            players.forEach(p -> counts.put(p.id(), p.handSize()));

            Optional<Claim> claim = game.getLastDiscard()
                    .map(BotObservation::claimOf);

            return new BotObservation(
                    seat,
                    me.getCards(),
                    counts,
                    game.getCurrentTarget(),
                    game.getDiscardPileSize(),
                    claim,
                    game.getCurrentPlayer().id(),
                    game.getPendingWinner(),
                    game.getAvailableActions(seat));
        }
    }

    private static Claim claimOf(Discard discard) {
        return new Claim(discard.claimant(), discard.claimedTarget(), discard.actualCards().size());
    }

    public boolean can(Action action) {
        return actions.contains(action);
    }
}
