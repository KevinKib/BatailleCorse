package org.kevinkib.cardgames.president.domain.bot;

import org.kevinkib.cardgames.president.domain.card.HandSort;
import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cardgames.president.domain.exchange.PendingReturn;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * One heuristic for the three levels (the level is only its {@link PresidentBotTuning}). Every candidate comes
 * from the domain's legal plays, so a rule of the game that changes can never make the bot play an illegal card;
 * the heuristic only ranks them:
 *
 * <ul>
 *   <li>lead the lowest set that does not break a bigger set, control cards (the strongest ranks, and sets
 *       nobody can beat when the level counts cards) kept for the end;</li>
 *   <li>follow with the lowest beating set that does not break a set; pass rather than break a pair or spend a
 *       control card, unless the hand is short or the play goes out;</li>
 *   <li>never go out on a two when that finish is forbidden (a cutting two included); the hard level also leads its
 *       twos first so that a low set stays for last;</li>
 *   <li>keep a cutting two like any control card: it is played on a low trick only when the hand is short; under
 *       compulsory play, which refuses the pass, it plays the weakest legal play instead;</li>
 *   <li>give back the lowest cards of the exchange without breaking a pair;</li>
 *   <li>under the equal-play binding the candidates are the matching sets and a pass (the domain lists them);
 *       a completion of the four is taken, in turn or out of turn, unless the level declines it.</li>
 * </ul>
 *
 * The easy level sometimes plays a random legal set or passes at random.
 */
public final class HeuristicPresidentBot implements PresidentBotStrategy {

    private static final RankOrder ORDER = RankOrder.standard();
    private static final int RANKS = 13;

    private final PresidentBotTuning tuning;
    private final RandomGenerator random;

    public HeuristicPresidentBot(PresidentBotTuning tuning, RandomGenerator random) {
        this.tuning = tuning;
        this.random = random;
    }

    @Override
    public PresidentBotDecision decide(PresidentBotView view, PresidentBotMemory memory) {
        return switch (view.phase()) {
            case EXCHANGE -> {
                require(view, Action.GIVE_BACK);
                yield giveBack(view);
            }
            case ROUND_OVER -> {
                require(view, Action.READY);
                yield new PresidentBotDecision.Ready();
            }
            case PLAYING -> decideTurn(view, memory);
            case FINISHED -> throw new IllegalStateException("The match is over");
        };
    }

    private static void require(PresidentBotView view, Action action) {
        if (!view.can(action)) {
            throw new IllegalStateException("Seat " + view.seat() + " cannot " + action + " now");
        }
    }

    private boolean chance(double probability) {
        return probability > 0 && random.nextDouble() < probability;
    }

    // ----- the turn -----

    private PresidentBotDecision decideTurn(PresidentBotView view, PresidentBotMemory memory) {
        boolean canPlay = view.can(Action.PLAY);
        boolean canPass = view.can(Action.PASS);
        boolean canComplete = view.can(Action.COMPLETE);
        if (canComplete && takesCompletion(view)) {
            // it closes the trick and gives the lead, in turn or out of turn
            return new PresidentBotDecision.Complete(view.completion());
        }
        if (!canPlay && !canPass) {
            if (canComplete) {
                return new PresidentBotDecision.Keep(); // out of turn, and the cards are kept
            }
            throw new IllegalStateException("Seat " + view.seat() + " has nothing to do");
        }
        if (!canPlay) {
            return new PresidentBotDecision.Pass();
        }
        boolean leading = view.trick().plays().isEmpty();
        List<Play> legal = view.legalPlays();
        if (!leading && canPass && chance(tuning.randomPassProbability())) {
            return new PresidentBotDecision.Pass();
        }
        if (chance(tuning.randomPlayProbability())) {
            return play(legal.get(random.nextInt(legal.size())));
        }
        if (leading) {
            return lead(view, memory, legal);
        }
        PresidentBotDecision decision = follow(view, legal);
        if (decision instanceof PresidentBotDecision.Pass && !canPass) {
            // compulsory play refuses the pass: the weakest play, one that is not a forbidden finish if there is one
            List<Play> allowed = withoutForbiddenFinish(view, legal);
            return play(allowed.isEmpty() ? legal.get(0) : allowed.get(0));
        }
        return decision;
    }

    /**
     * Completion is optional (spec 3.1.1). The level decides how often it is taken; the hard level also keeps
     * the cards that would be its last ones and a forbidden finish (twos).
     */
    private boolean takesCompletion(PresidentBotView view) {
        if (tuning.keepsDangerousCompletion()
                && view.completion().size() == view.hand().size()
                && view.completion().get(0).getRank() == FrenchRank.TWO) {
            return false;
        }
        return tuning.completionProbability() >= 1 || chance(tuning.completionProbability());
    }

    private static PresidentBotDecision play(Play play) {
        return new PresidentBotDecision.Play(play.cards());
    }

    private PresidentBotDecision lead(PresidentBotView view, PresidentBotMemory memory, List<Play> legal) {
        List<Play> allowed = withoutForbiddenFinish(view, legal);
        if (allowed.isEmpty()) {
            return play(legal.get(0));
        }
        Play goingOut = goingOut(view, allowed);
        if (goingOut != null) {
            return play(goingOut);
        }
        Map<FrenchRank, Integer> groups = groupSizes(view.hand());
        List<Play> sets = unbroken(allowed, groups);
        if (sets.isEmpty()) {
            return play(allowed.get(0));
        }
        if (tuning.plansEndGame()) {
            Play twos = keepLowCardForLast(sets, groups);
            if (twos != null) {
                return play(twos);
            }
        }
        for (Play set : sets) {
            if (!isControl(set, view, memory)) {
                return play(set);
            }
        }
        return play(sets.get(0));
    }

    private PresidentBotDecision follow(PresidentBotView view, List<Play> legal) {
        List<Play> allowed = withoutForbiddenFinish(view, legal);
        if (allowed.isEmpty()) {
            return new PresidentBotDecision.Pass();
        }
        Play goingOut = goingOut(view, allowed);
        if (goingOut != null) {
            return play(goingOut);
        }
        boolean shortHand = view.hand().size() <= tuning.shortHand();
        List<Play> sets = unbroken(allowed, groupSizes(view.hand()));
        if (!sets.isEmpty()) {
            Play lowest = sets.get(0);
            return isStrong(lowest) && !shortHand ? new PresidentBotDecision.Pass() : play(lowest);
        }
        return shortHand ? play(allowed.get(0)) : new PresidentBotDecision.Pass();
    }

    // ----- helpers for the turn -----

    private static Map<FrenchRank, Integer> groupSizes(List<Card> hand) {
        Map<FrenchRank, Integer> sizes = new LinkedHashMap<>();
        for (Card card : HandSort.sorted(hand, ORDER)) {
            sizes.merge((FrenchRank) card.getRank(), 1, Integer::sum);
        }
        return sizes;
    }

    /** The plays that use every card of their rank in the hand, weakest first. */
    private static List<Play> unbroken(List<Play> plays, Map<FrenchRank, Integer> groups) {
        return plays.stream().filter(p -> p.count() == groups.get(p.rank())).toList();
    }

    /** Going out on a two is refused by the forbidden-finish rule: those plays are never candidates. */
    private static List<Play> withoutForbiddenFinish(PresidentBotView view, List<Play> plays) {
        return plays.stream()
                .filter(p -> !(p.count() == view.hand().size() && p.rank() == FrenchRank.TWO))
                .toList();
    }

    private static Play goingOut(PresidentBotView view, List<Play> plays) {
        return plays.stream().filter(p -> p.count() == view.hand().size()).findFirst().orElse(null);
    }

    /** The strongest ranks, which the bot keeps for the end. */
    private boolean isStrong(Play play) {
        return ORDER.strength(play.rank()) >= RANKS - tuning.controlRanks();
    }

    private boolean isControl(Play play, PresidentBotView view, PresidentBotMemory memory) {
        return isStrong(play) || memory.isUnbeatable(play, view.hand(), tuning.countedRanks(), ORDER);
    }

    /**
     * Under a forbidden finish the last play must not be a two: when the hand is some twos and a single other
     * set, the twos go first and the other set goes out last.
     */
    private static Play keepLowCardForLast(List<Play> sets, Map<FrenchRank, Integer> groups) {
        boolean oneOtherSet = groups.keySet().stream().filter(rank -> rank != FrenchRank.TWO).count() == 1;
        if (!oneOtherSet) {
            return null;
        }
        return sets.stream().filter(p -> p.rank() == FrenchRank.TWO).findFirst().orElse(null);
    }

    // ----- exchange -----

    /** The lowest cards, whole sets first: a pair is only broken when no other card can make up the count. */
    private PresidentBotDecision giveBack(PresidentBotView view) {
        int owed = view.pendingReturns().stream()
                .filter(p -> p.high().equals(view.seat()) && !p.done())
                .mapToInt(PendingReturn::count)
                .sum();
        Map<FrenchRank, List<Card>> groups = new LinkedHashMap<>();
        for (Card card : HandSort.sorted(view.hand(), ORDER)) {
            groups.computeIfAbsent((FrenchRank) card.getRank(), r -> new ArrayList<>()).add(card);
        }
        List<Card> chosen = new ArrayList<>();
        List<FrenchRank> taken = new ArrayList<>();
        for (Map.Entry<FrenchRank, List<Card>> group : groups.entrySet()) {
            if (group.getValue().size() <= owed - chosen.size()) {
                chosen.addAll(group.getValue());
                taken.add(group.getKey());
            }
        }
        for (Map.Entry<FrenchRank, List<Card>> group : groups.entrySet()) {
            if (chosen.size() >= owed) {
                break;
            }
            if (!taken.contains(group.getKey())) {
                chosen.addAll(group.getValue().subList(0, Math.min(owed - chosen.size(), group.getValue().size())));
            }
        }
        return new PresidentBotDecision.GiveBack(HandSort.sorted(chosen, ORDER));
    }
}
