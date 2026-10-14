package org.kevinkib.cardgames.kobo.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.bot.KoboBotMemory.Known;
import org.kevinkib.cardgames.kobo.domain.card.CardPoints;
import org.kevinkib.cardgames.kobo.domain.scoring.RoundResult;
import org.kevinkib.cardgames.kobo.domain.rules.KoboRules;
import org.kevinkib.cardgames.kobo.domain.tableau.SlotRef;
import org.kevinkib.cardgames.kobo.domain.turn.Action;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.Rank;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One heuristic for the three levels (the level is only its {@link KoboBotTuning}). No search and no card
 * counting: a card the bot has not seen is worth the deck average, a card it remembers is worth its points, and
 * every rule below follows from that. It never guesses a match: it only matches a card it remembers.
 */
public final class HeuristicKoboBot implements KoboBotStrategy {

    /** The average value of a card of the deck (338 points over 52 cards), computed from {@link CardPoints}. */
    static final double UNKNOWN_VALUE = deckAverage();

    private static final int SWAP_MIN_GAIN = 1;
    private static final int BLIND_SWAP_MIN_GAIN = 3;
    private static final int KING_SWAP_MIN_GAIN = 2;
    private static final int KING_PEEK_MAX_POINTS = 3;
    /** Own turns after which the announce threshold starts to grow, and how many turns per extra point. */
    private static final int PRESSURE_FROM_TURN = 6;
    private static final int PRESSURE_STEP = 3;

    private final KoboBotTuning tuning;

    public HeuristicKoboBot(KoboBotTuning tuning) {
        this.tuning = tuning;
    }

    private static double deckAverage() {
        int total = 0;
        int count = 0;
        for (FrenchRank rank : FrenchRank.values()) {
            if (rank == FrenchRank.JOKER) {
                continue;
            }
            for (FrenchSuit suit : new FrenchSuit[]{FrenchSuit.HEART, FrenchSuit.DIAMOND, FrenchSuit.CLUB,
                    FrenchSuit.SPADE}) {
                total += CardPoints.of(new Card(rank, suit));
                count++;
            }
        }
        return (double) total / count;
    }

    // ----------------------------------------------------------------- own steps

    @Override
    public KoboBotDecision decide(KoboBotView view, KoboBotMemory memory) {
        return switch (view.phase()) {
            case MEMORISING, ROUND_OVER -> require(view, Action.READY, new KoboBotDecision.Ready());
            case DRAW -> require(view, Action.DRAW, new KoboBotDecision.Draw());
            case DECISION -> {
                require(view, Action.DISCARD_DRAWN, null);
                yield decideDrawn(view, memory);
            }
            case POWER -> {
                require(view, Action.SKIP_POWER, null);
                yield decidePower(view, memory);
            }
            case KING_DECISION -> {
                require(view, Action.KING_SWAP, null);
                yield decideKing(view, memory);
            }
            case TURN_END -> {
                require(view, Action.END_TURN, null);
                yield view.can(Action.ANNOUNCE_KOBO) && shouldAnnounce(view, memory)
                        ? new KoboBotDecision.AnnounceKobo() : new KoboBotDecision.EndTurn();
            }
            case GIFT -> require(view, Action.GIVE_TEN, new KoboBotDecision.GiveTen(giftTarget(view)));
            case FINISHED -> throw new IllegalStateException("The game is finished");
        };
    }

    private static KoboBotDecision require(KoboBotView view, Action action, KoboBotDecision decision) {
        if (!view.can(action)) {
            throw new IllegalStateException("Nothing to decide for " + view.seat() + " in " + view.phase());
        }
        return decision;
    }

    private KoboBotDecision decideDrawn(KoboBotView view, KoboBotMemory memory) {
        Card drawn = view.drawn().orElseThrow(() -> new IllegalStateException("No drawn card to decide on"));
        int drawnPoints = CardPoints.of(drawn);
        Integer best = null;
        double bestGain = 0;
        boolean bestKnown = false;
        for (KoboBotView.SlotView slot : occupied(view, view.seat())) {
            SlotRef ref = new SlotRef(view.seat(), slot.slot());
            boolean known = memory.known(ref).isPresent();
            double gain = estimate(memory, ref) - drawnPoints;
            if (best == null || gain > bestGain || (gain == bestGain && known && !bestKnown)) {
                best = slot.slot();
                bestGain = gain;
                bestKnown = known;
            }
        }
        if (best != null && bestGain >= SWAP_MIN_GAIN) {
            return new KoboBotDecision.SwapDrawn(best);
        }
        return new KoboBotDecision.DiscardDrawn();
    }

    private KoboBotDecision decidePower(KoboBotView view, KoboBotMemory memory) {
        return switch (view.pendingPower()) {
            case PEEK_OWN -> unknownSlot(view, memory, view.seat())
                    .<KoboBotDecision>map(KoboBotDecision.Peek::new).orElseGet(KoboBotDecision.SkipPower::new);
            case PEEK_OTHER -> opponentsByEstimate(view, memory).stream()
                    .map(opponent -> unknownSlot(view, memory, opponent))
                    .flatMap(Optional::stream).findFirst()
                    .<KoboBotDecision>map(KoboBotDecision.Peek::new).orElseGet(KoboBotDecision.SkipPower::new);
            case BLIND_SWAP -> decideBlindSwap(view, memory);
            case KING -> decideKingPeek(view, memory);
            case NONE -> new KoboBotDecision.SkipPower();
        };
    }

    private KoboBotDecision decideBlindSwap(KoboBotView view, KoboBotMemory memory) {
        Optional<SlotRef> mine = worstOwnSlot(view, memory);
        Optional<SlotRef> theirs = lowestOpponentSlot(view, memory);
        if (mine.isPresent() && theirs.isPresent()
                && estimate(memory, mine.get()) - estimate(memory, theirs.get()) >= BLIND_SWAP_MIN_GAIN) {
            return new KoboBotDecision.BlindSwap(mine.get().slot(), theirs.get());
        }
        return new KoboBotDecision.SkipPower();
    }

    private KoboBotDecision decideKingPeek(KoboBotView view, KoboBotMemory memory) {
        Optional<Known> cheap = view.opponents().stream()
                .flatMap(opponent -> memory.knownSlots(opponent).stream())
                .filter(known -> CardPoints.of(known.card()) <= KING_PEEK_MAX_POINTS)
                .filter(known -> slotMatches(view, known))
                .min(Comparator.comparingInt(known -> CardPoints.of(known.card())));
        if (cheap.isPresent()) {
            return new KoboBotDecision.Peek(cheap.get().ref());
        }
        return opponentsByEstimate(view, memory).stream()
                .map(opponent -> unknownSlot(view, memory, opponent))
                .flatMap(Optional::stream).findFirst()
                .<KoboBotDecision>map(KoboBotDecision.Peek::new).orElseGet(KoboBotDecision.SkipPower::new);
    }

    private KoboBotDecision decideKing(KoboBotView view, KoboBotMemory memory) {
        Card peeked = view.reveal().orElseThrow(() -> new IllegalStateException("No peeked card")).card();
        Optional<SlotRef> worst = worstOwnSlot(view, memory);
        if (worst.isPresent() && estimate(memory, worst.get()) - CardPoints.of(peeked) >= KING_SWAP_MIN_GAIN) {
            return new KoboBotDecision.KingSwap(worst.get().slot());
        }
        return new KoboBotDecision.SkipPower();
    }

    // -------------------------------------------------------------- announcing

    private boolean shouldAnnounce(KoboBotView view, KoboBotMemory memory) {
        List<KoboBotView.SlotView> hand = occupied(view, view.seat());
        if (hand.isEmpty()) {
            return true;
        }
        double own = handEstimate(view, memory, view.seat());
        long unknown = hand.stream()
                .filter(slot -> memory.known(new SlotRef(view.seat(), slot.slot())).isEmpty()).count();
        if (unknown > tuning.maxUnknownAtAnnounce() || own > tuning.announceAt() + pressure(memory)) {
            return false;
        }
        if (!tuning.comparesOpponents()) {
            return true;
        }
        return view.opponents().stream()
                .allMatch(opponent -> own < handEstimate(view, memory, opponent) - tuning.announceMargin());
    }

    /** One more point of tolerance every few own turns after the sixth: a bots-only round always ends. */
    private static int pressure(KoboBotMemory memory) {
        int turn = memory.ownTurns() + 1;
        return turn > PRESSURE_FROM_TURN ? (turn - PRESSURE_FROM_TURN - 1) / PRESSURE_STEP + 1 : 0;
    }

    // ---------------------------------------------------------------- the gift

    private PlayerId giftTarget(KoboBotView view) {
        List<PlayerId> others = view.opponents();
        Optional<RoundResult> round = view.lastRound();
        if (round.isEmpty()) {
            return others.get(0);
        }
        var provisional = round.get().totalsAfter();
        int mine = provisional.get(view.seat());

        // an opponent pushed above the end score while the bot then has the lowest total: the bot wins
        for (PlayerId opponent : others) {
            if (provisional.get(opponent) + KoboRules.GIFT_POINTS > KoboRules.END_SCORE
                    && others.stream().allMatch(other -> mine < totalAfterGift(provisional.get(other), other, opponent))) {
                return opponent;
            }
        }
        // otherwise the lowest total, unless the 10 points would reset it to 50 (that would help it)
        return others.stream()
                .filter(opponent -> !resets(provisional.get(opponent) + KoboRules.GIFT_POINTS))
                .min(Comparator.comparingInt(provisional::get))
                .orElse(others.get(0));
    }

    private static int totalAfterGift(int total, PlayerId player, PlayerId receiver) {
        return player.equals(receiver) ? total + KoboRules.GIFT_POINTS : total;
    }

    private static boolean resets(int total) {
        for (int threshold : KoboRules.RESET_TOTALS) {
            if (total == threshold) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------- match

    @Override
    public Optional<KoboBotDecision.Match> matchFor(KoboBotView view, KoboBotMemory memory) {
        if (!view.can(Action.MATCH_DISCARD) || view.discardTop().isEmpty()) {
            return Optional.empty();
        }
        Rank rank = view.discardTop().get().getRank();
        List<Known> candidates = memory.knownOfRank(rank).stream().filter(known -> slotMatches(view, known)).toList();
        Optional<Known> own = candidates.stream().filter(known -> known.ref().seat().equals(view.seat())).findFirst();
        if (own.isPresent()) {
            return Optional.of(new KoboBotDecision.Match(own.get().ref(), null, rank, own.get().revision()));
        }
        if (!tuning.matchesOpponents() || candidates.isEmpty()) {
            return Optional.empty();
        }
        Known theirs = candidates.get(0);
        Integer give = worstOwnSlot(view, memory).map(SlotRef::slot).orElse(null);
        return Optional.of(new KoboBotDecision.Match(theirs.ref(), give, rank, theirs.revision()));
    }

    /** Defensive: the remembered card is still where and as the view says (never match on a stale memory). */
    private static boolean slotMatches(KoboBotView view, Known known) {
        List<KoboBotView.SlotView> slots = view.tableaux().get(known.ref().seat());
        if (slots == null || known.ref().slot() >= slots.size()) {
            return false;
        }
        KoboBotView.SlotView slot = slots.get(known.ref().slot());
        return slot.occupied() && slot.revision() == known.revision();
    }

    // ----------------------------------------------------------------- helpers

    private static List<KoboBotView.SlotView> occupied(KoboBotView view, PlayerId player) {
        return view.slots(player).stream().filter(KoboBotView.SlotView::occupied).toList();
    }

    private static double estimate(KoboBotMemory memory, SlotRef ref) {
        return memory.known(ref).map(known -> (double) CardPoints.of(known.card())).orElse(UNKNOWN_VALUE);
    }

    private static double handEstimate(KoboBotView view, KoboBotMemory memory, PlayerId player) {
        return occupied(view, player).stream()
                .mapToDouble(slot -> estimate(memory, new SlotRef(player, slot.slot()))).sum();
    }

    private static Optional<SlotRef> unknownSlot(KoboBotView view, KoboBotMemory memory, PlayerId player) {
        return occupied(view, player).stream()
                .map(slot -> new SlotRef(player, slot.slot()))
                .filter(ref -> memory.known(ref).isEmpty())
                .findFirst();
    }

    /** Opponents from the lowest estimated hand (the likeliest Kobo) to the highest; ties by seat order. */
    private static List<PlayerId> opponentsByEstimate(KoboBotView view, KoboBotMemory memory) {
        return view.opponents().stream()
                .sorted(Comparator.comparingDouble(opponent -> handEstimate(view, memory, opponent)))
                .toList();
    }

    /** The own card with the highest estimate; on a tie a known one, then the lowest slot. */
    private static Optional<SlotRef> worstOwnSlot(KoboBotView view, KoboBotMemory memory) {
        return occupied(view, view.seat()).stream()
                .map(slot -> new SlotRef(view.seat(), slot.slot()))
                .max(Comparator.<SlotRef>comparingDouble(ref -> estimate(memory, ref))
                        .thenComparing(ref -> memory.known(ref).isPresent())
                        .thenComparing(Comparator.comparingInt(SlotRef::slot).reversed()));
    }

    /** The opponent card with the lowest estimate; on a tie the opponent with the lowest hand, then the lowest slot. */
    private static Optional<SlotRef> lowestOpponentSlot(KoboBotView view, KoboBotMemory memory) {
        return opponentsByEstimate(view, memory).stream()
                .flatMap(opponent -> occupied(view, opponent).stream()
                        .map(slot -> new SlotRef(opponent, slot.slot())))
                .min(Comparator.<SlotRef>comparingDouble(ref -> estimate(memory, ref)));
    }
}
