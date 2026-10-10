package org.kevinkib.cardgames.bullshit.domain.bot;

import org.kevinkib.cardgames.bullshit.domain.Action;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimTarget;
import org.kevinkib.cardgames.bullshit.domain.claim.RankTarget;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Builds hand-made observations for strategy tests (actions are derived from the table state). */
public final class ObservationFixtures {

    private PlayerId me = new PlayerId(0);
    private List<Card> hand = new ArrayList<>();
    private final Map<PlayerId, Integer> otherCounts = new LinkedHashMap<>();
    private ClaimTarget target = new RankTarget(FrenchRank.ACE);
    private Optional<BotObservation.Claim> claim = Optional.empty();
    private PlayerId currentPlayer = new PlayerId(0);
    private Optional<PlayerId> pendingWinner = Optional.empty();

    private ObservationFixtures() {
        otherCounts.put(new PlayerId(1), 10);
        otherCounts.put(new PlayerId(2), 10);
    }

    public static ObservationFixtures anObservation() {
        return new ObservationFixtures();
    }

    public static Card card(FrenchRank rank, FrenchSuit suit) {
        return new Card(rank, suit);
    }

    public ObservationFixtures hand(Card... cards) {
        this.hand = new ArrayList<>(List.of(cards));
        return this;
    }

    public ObservationFixtures target(ClaimTarget target) {
        this.target = target;
        return this;
    }

    public ObservationFixtures handCount(int seat, int count) {
        otherCounts.put(new PlayerId(seat), count);
        return this;
    }

    public ObservationFixtures currentPlayer(int seat) {
        this.currentPlayer = new PlayerId(seat);
        return this;
    }

    public ObservationFixtures claim(int claimant, ClaimTarget claimed, int count) {
        this.claim = Optional.of(new BotObservation.Claim(new PlayerId(claimant), claimed, count));
        return this;
    }

    public ObservationFixtures pendingWinner(int seat) {
        this.pendingWinner = Optional.of(new PlayerId(seat));
        return this;
    }

    public BotObservation build() {
        Map<PlayerId, Integer> counts = new LinkedHashMap<>();
        counts.put(me, hand.size());
        counts.putAll(otherCounts);
        List<Action> actions = new ArrayList<>();
        if (currentPlayer.equals(me)) {
            actions.add(Action.DISCARD);
        }
        if (claim.isPresent() && !claim.get().claimant().equals(me)) {
            actions.add(Action.CALL_BULLSHIT);
        }
        return new BotObservation(me, hand, counts, target, 0, claim, currentPlayer, pendingWinner, actions);
    }
}
