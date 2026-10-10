package org.kevinkib.cardgames.bullshit.domain.bot;

import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitBuilder;
import org.kevinkib.cardgames.bullshit.domain.CallBullshitOutcome;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimMode;
import org.kevinkib.cardgames.bullshit.domain.pile.Discard;
import org.kevinkib.cardgames.bullshit.domain.player.Player;
import org.kevinkib.cardgames.bullshit.domain.player.PlayerBuilder;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;
import org.kevinkib.cards.testhelpers.HandBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;

/**
 * Plays a game between bots only, deterministically: seeded deal, seeded randomness, no scheduler, no
 * sleeping. Memories are fed as the bot coordinator feeds them (own discards, revealed cards, pile taken).
 */
final class BotSimulation {

    record Outcome(boolean finished, int actions) {
    }

    private BotSimulation() {
    }

    /** A game whose deal is fixed by the seed (the production deal shuffles with its own entropy). */
    static Bullshit deal(ClaimMode mode, int seats, long seed) {
        List<Card> deck = new ArrayList<>();
        for (FrenchRank rank : FrenchRank.getRanks()) {
            for (FrenchSuit suit : FrenchSuit.getSuits()) {
                deck.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(deck, new Random(seed * 1_000_003L + 11));
        List<List<Card>> hands = new ArrayList<>();
        for (int i = 0; i < seats; i++) {
            hands.add(new ArrayList<>());
        }
        for (int i = 0; i < deck.size(); i++) {
            hands.get(i % seats).add(deck.get(i));
        }
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < seats; i++) {
            players.add(PlayerBuilder.aPlayer().withId(i)
                    .withHand(HandBuilder.aHand().withCards(hands.get(i)).build()).build());
        }
        return BullshitBuilder.aBullshit().withPlayers(players).withClaimMode(mode).build();
    }

    static Outcome play(ClaimMode mode, int seats, long seed, int actionCap) {
        return play(mode, seats, seed, actionCap,
                (random, m) -> new ProbabilisticBotStrategy(m, random, BotTuning.DEFAULT));
    }

    static Outcome play(ClaimMode mode, int seats, long seed, int actionCap,
                        BiFunction<Random, ClaimMode, BotStrategy> strategyFactory) {
        Random random = new Random(seed);
        Random order = new Random(seed * 31 + 7);
        Bullshit game = deal(mode, seats, seed);
        BotStrategy strategy = strategyFactory.apply(random, mode);
        Map<PlayerId, BotMemory> memories = new HashMap<>();
        for (PlayerId seat : game.getPlayerIds()) {
            memories.put(seat, new BotMemory(seat));
        }
        PlayerId unresolvedClaimant = null;
        int actions = 0;
        try {
            while (!game.isFinished() && actions < actionCap) {
                List<PlayerId> candidates = new ArrayList<>(game.getPlayerIds());
                Collections.shuffle(candidates, order);
                boolean acted = false;
                for (PlayerId seat : candidates) {
                    if (game.getAvailableActions(seat).isEmpty()) {
                        continue;
                    }
                    BotMemory memory = memories.get(seat);
                    BotDecision decision = strategy.decide(BotObservation.forSeat(game, seat), memory);
                    if (decision instanceof BotDecision.CallBullshit) {
                        Discard challenged = game.getLastDiscard().orElseThrow();
                        CallBullshitOutcome outcome = game.callBullshit(seat);
                        actions++;
                        if (unresolvedClaimant != null) {
                            PlayerId claimant = unresolvedClaimant;
                            memories.values().forEach(m -> m.onDiscardBy(claimant));
                            unresolvedClaimant = null;
                        }
                        for (BotMemory m : memories.values()) {
                            m.onReveal(outcome.pilePicker(), challenged.actualCards());
                            m.onPileTaken();
                        }
                        acted = true;
                        break;
                    } else if (decision instanceof BotDecision.Discard discard) {
                        game.discard(seat, discard.cards());
                        actions++;
                        if (unresolvedClaimant != null) {
                            PlayerId claimant = unresolvedClaimant;
                            memories.values().forEach(m -> m.onDiscardBy(claimant));
                        }
                        unresolvedClaimant = seat;
                        memory.onOwnDiscard(discard.cards());
                        acted = true;
                        break;
                    }
                }
                if (!acted) {
                    throw new AssertionError("Nobody could act");
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return new Outcome(game.isFinished(), actions);
    }
}
