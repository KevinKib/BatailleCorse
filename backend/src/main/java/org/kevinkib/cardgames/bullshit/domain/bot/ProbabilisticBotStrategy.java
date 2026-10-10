package org.kevinkib.cardgames.bullshit.domain.bot;

import org.kevinkib.cardgames.bullshit.domain.Action;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimTarget;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * The single difficulty level: it counts the cards it can account for to estimate how plausible a
 * claim is, challenges with some randomness, plays honestly when it can and lies cheaply when it
 * cannot. Every random choice draws one {@code nextDouble()} so tests can script the branches.
 */
public final class ProbabilisticBotStrategy implements BotStrategy {

    private static final BotDecision PASS = new BotDecision.Pass();
    private static final int HORIZON = 64;
    private static final int PLANNING_HAND = 8;

    private final ClaimMode claimMode;
    private final RandomGenerator random;
    private final BotTuning tuning;

    public ProbabilisticBotStrategy(ClaimMode claimMode, RandomGenerator random, BotTuning tuning) {
        this.claimMode = claimMode;
        this.random = random;
        this.tuning = tuning;
    }

    @Override
    public BotDecision decide(BotObservation observation, BotMemory memory) {
        if (canWinNow(observation)) {
            return new BotDecision.Discard(observation.hand());
        }
        if (observation.can(Action.CALL_BULLSHIT)
                && (mustCallInsteadOfLosingLastCard(observation) || shouldCall(observation, memory))) {
            return new BotDecision.CallBullshit();
        }
        if (observation.can(Action.DISCARD) && !observation.hand().isEmpty()) {
            return new BotDecision.Discard(chooseCards(observation));
        }
        return PASS;
    }

    /**
     * Playing the whole hand honestly wins on the spot: the next seat can only challenge a true claim
     * (which ends the game in the bot's favour) or decline it (which does the same).
     */
    private boolean canWinNow(BotObservation obs) {
        return obs.can(Action.DISCARD) && obs.pendingWinner().isEmpty() && !obs.hand().isEmpty() && obs.hand().size() <= tuning.maxCardsPerPlay()
                && claimMode.matches(obs.hand(), obs.currentTarget());
    }

    /**
     * Lying with the last card hands the next seat a certain challenge, so a bot holding one card
     * that cannot be played honestly challenges the claim instead of playing it.
     */
    private boolean mustCallInsteadOfLosingLastCard(BotObservation obs) {
        return obs.can(Action.DISCARD) && obs.hand().size() == 1
                && !matches(obs.hand().get(0), obs.currentTarget());
    }

    // ---- challenge ---------------------------------------------------------------------------

    private boolean shouldCall(BotObservation obs, BotMemory memory) {
        BotObservation.Claim claim = obs.lastClaim().orElseThrow();
        boolean declineEndsTheGame = obs.pendingWinner().isPresent() && obs.currentPlayer().equals(obs.me());
        if (declineEndsTheGame) {
            return true;
        }
        double pTrue = probabilityTrue(obs, memory, claim);
        double pCall = clamp(1.0 - pTrue, tuning.minCallProbability(), tuning.maxCallProbability());
        return random.nextDouble() < pCall;
    }

    private double probabilityTrue(BotObservation obs, BotMemory memory, BotObservation.Claim claim) {
        int unseen = memory.unseenPopulation(obs.hand());
        int matching = Math.min(memory.unseenMatching(obs.hand(), claim.target(), claimMode), unseen);
        int handBefore = Math.min(obs.handCounts().getOrDefault(claim.claimant(), 0) + claim.count(), unseen);
        int known = memory.knownMatches(claim.claimant(), claim.target(), claimMode);
        known = Math.min(known, Math.min(matching, handBefore));
        return PlausibilityEstimator.probabilityGenuine(claim.count(), matching, known, unseen, handBefore, priorFor(claim.count()));
    }

    private PlausibilityEstimator.ClaimPrior priorFor(int claimedCount) {
        double liar = switch (claimedCount) {
            case 1 -> tuning.lieOneCardProbability();
            case 2 -> tuning.lieTwoCardsProbability();
            case 3 -> 1.0 - tuning.lieOneCardProbability() - tuning.lieTwoCardsProbability();
            default -> 0.0;
        };
        return new PlausibilityEstimator.ClaimPrior(tuning.honestBluffProbability(), liar, tuning.maxCardsPerPlay());
    }

    // ---- play --------------------------------------------------------------------------------

    private List<Card> chooseCards(BotObservation obs) {
        ClaimTarget target = obs.currentTarget();
        List<Card> hand = obs.hand();
        List<Card> matching = new ArrayList<>();
        List<Card> others = new ArrayList<>();
        for (Card card : hand) {
            (matches(card, target) ? matching : others).add(card);
        }

        if (matching.isEmpty()) {
            return lie(hand, target, obs.handCounts().size());
        }
        if (others.isEmpty() && hand.size() <= tuning.maxCardsPerPlay()) {
            return new ArrayList<>(hand);
        }
        if (matching.size() >= tuning.maxCardsPerPlay()) {
            return new ArrayList<>(matching.subList(0, tuning.maxCardsPerPlay()));
        }

        boolean bluff = random.nextDouble() < tuning.honestBluffProbability();
        List<Card> play = new ArrayList<>(matching);
        if (bluff && !others.isEmpty()) {
            play.add(latestNeeded(others, target, obs.handCounts().size()).get(0));
        }
        return play;
    }

    private List<Card> lie(List<Card> hand, ClaimTarget target, int seats) {
        double draw = random.nextDouble();
        int count;
        if (draw < tuning.lieOneCardProbability()) {
            count = 1;
        } else if (draw < tuning.lieOneCardProbability() + tuning.lieTwoCardsProbability()) {
            count = 2;
        } else {
            count = 3;
        }
        int ceiling = hand.size() > 1 ? hand.size() - 1 : 1; // a lie never empties the hand: it would be challenged for sure
        count = Math.min(count, Math.min(ceiling, tuning.maxCardsPerPlay()));
        if (hand.size() <= PLANNING_HAND) {
            return planEndgameLie(hand, target, seats, count);
        }
        return new ArrayList<>(latestNeeded(hand, target, seats).subList(0, count));
    }

    /**
     * A small hand can only be emptied by an honest last play, so it picks the card group to finish
     * with: the one whose target lands on the bot's own turn right after the other cards have been
     * lied away, one or a few per turn. The cards outside that group are what the lie gets rid of.
     */
    private List<Card> planEndgameLie(List<Card> hand, ClaimTarget target, int seats, int drawnCount) {
        Map<Integer, List<Card>> groups = new HashMap<>();
        for (Card card : hand) {
            groups.computeIfAbsent(turnsUntilNeeded(card, target, seats), k -> new ArrayList<>()).add(card);
        }
        int bestTurns = -1;
        int bestCost = Integer.MAX_VALUE;
        for (Map.Entry<Integer, List<Card>> group : groups.entrySet()) {
            int turns = group.getKey();
            int size = group.getValue().size();
            if (turns > HORIZON || size > tuning.maxCardsPerPlay()) {
                continue;
            }
            int burn = hand.size() - size;
            int cost = Math.abs(turns - 1 - burn);
            if (cost < bestCost || (cost == bestCost && turns < bestTurns)) {
                bestCost = cost;
                bestTurns = turns;
            }
        }
        if (bestTurns < 0) {
            return new ArrayList<>(latestNeeded(hand, target, seats).subList(0, drawnCount));
        }

        List<Card> keep = groups.get(bestTurns);
        List<Card> burnable = new ArrayList<>(hand);
        burnable.removeAll(keep);
        if (burnable.isEmpty()) {
            return new ArrayList<>(keep.subList(0, 1));
        }
        int perTurn = (burnable.size() + bestTurns - 1) / bestTurns;
        int count = Math.min(burnable.size(), Math.min(Math.max(1, perTurn), tuning.maxCardsPerPlay()));
        return new ArrayList<>(latestNeeded(burnable, target, seats).subList(0, count));
    }

    /** Cards ordered by how long until their own target comes up: the longest wait first. */
    private List<Card> latestNeeded(List<Card> cards, ClaimTarget current, int seats) {
        List<Card> sorted = new ArrayList<>(cards);
        sorted.sort(Comparator.comparingInt((Card card) -> turnsUntilNeeded(card, current, seats)).reversed());
        return sorted;
    }

    /**
     * How many of the bot's own turns from now until a card's target is claimed on its turn, assuming
     * nobody challenges in between: the target moves one step per play, so with {@code seats} players
     * the bot meets targets {@code seats}, {@code 2 * seats}... steps after the current one. A card
     * whose target never lands on the bot's turns counts as {@code HORIZON}.
     */
    private int turnsUntilNeeded(Card card, ClaimTarget current, int seats) {
        ClaimTarget target = current;
        for (int turns = 1; turns <= HORIZON; turns++) {
            for (int step = 0; step < seats; step++) {
                target = claimMode.next(target);
            }
            if (matches(card, target)) {
                return turns;
            }
        }
        return HORIZON + 1;
    }

    private boolean matches(Card card, ClaimTarget target) {
        return claimMode.matches(List.of(card), target);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
