package org.kevinkib.cardgames.bullshit.domain.bot;

import org.kevinkib.cardgames.bullshit.domain.Action;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimTarget;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The single difficulty level: it counts the cards it can account for to estimate how plausible a
 * claim is, challenges with some randomness, plays honestly when it can and lies cheaply when it
 * cannot. Every random choice draws one {@code nextDouble()} so tests can script the branches.
 */
public final class ProbabilisticBotStrategy implements BotStrategy {

    private static final BotDecision PASS = new BotDecision.Pass();
    private static final int HORIZON = 64;

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
        if (observation.can(Action.CALL_BULLSHIT) && shouldCall(observation, memory)) {
            return new BotDecision.CallBullshit();
        }
        if (observation.can(Action.DISCARD) && !observation.hand().isEmpty()) {
            return new BotDecision.Discard(chooseCards(observation));
        }
        return PASS;
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
        return PlausibilityEstimator.probabilityTrue(claim.count(), matching, known, unseen, handBefore);
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
            return lie(hand, target);
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
            play.add(latestNeeded(others, target).get(0));
        }
        return play;
    }

    private List<Card> lie(List<Card> hand, ClaimTarget target) {
        double draw = random.nextDouble();
        int count;
        if (draw < tuning.lieOneCardProbability()) {
            count = 1;
        } else if (draw < tuning.lieOneCardProbability() + tuning.lieTwoCardsProbability()) {
            count = 2;
        } else {
            count = 3;
        }
        count = Math.min(count, Math.min(hand.size(), tuning.maxCardsPerPlay()));
        return new ArrayList<>(latestNeeded(hand, target).subList(0, count));
    }

    /** Cards ordered by how long until their own target comes up: the longest wait first. */
    private List<Card> latestNeeded(List<Card> cards, ClaimTarget current) {
        List<Card> sorted = new ArrayList<>(cards);
        sorted.sort(Comparator.comparingInt((Card card) -> stepsUntilNeeded(card, current)).reversed());
        return sorted;
    }

    private int stepsUntilNeeded(Card card, ClaimTarget current) {
        ClaimTarget target = claimMode.next(current);
        for (int steps = 0; steps < HORIZON; steps++) {
            if (matches(card, target)) {
                return steps;
            }
            target = claimMode.next(target);
        }
        return HORIZON;
    }

    private boolean matches(Card card, ClaimTarget target) {
        return claimMode.matches(List.of(card), target);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
