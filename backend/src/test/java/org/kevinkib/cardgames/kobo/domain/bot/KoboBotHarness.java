package org.kevinkib.cardgames.kobo.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.event.KoboEvent;
import org.kevinkib.cardgames.kobo.domain.rules.KoboException;
import org.kevinkib.cardgames.kobo.domain.turn.Action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Plays a whole Kobo game with bots only, as the table would: after each command every bot is asked for a match in
 * a seeded order, then a seat with a turn step or a barrier decides. Memories are fed with the events like the
 * coordinator will do: public moves first, then the seat views.
 */
final class KoboBotHarness {

    private final Kobo kobo;
    private final List<KoboBotLevel> levels;
    private final List<KoboBotMemory> memories = new ArrayList<>();
    private final List<HeuristicKoboBot> bots = new ArrayList<>();
    private final Random random;
    int commands;
    int penalties;
    int matches;
    int fallbacks;

    KoboBotHarness(Kobo kobo, List<KoboBotLevel> levels, long seed) {
        this.kobo = kobo;
        this.levels = levels;
        this.random = new Random(seed);
        for (int i = 0; i < levels.size(); i++) {
            memories.add(new KoboBotMemory(new PlayerId(i), levels.get(i).tuning().followsMoves()));
            bots.add(new HeuristicKoboBot(levels.get(i).tuning()));
        }
        observeAll();
    }

    /** Runs until the game is finished or {@code maxCommands} were played; returns whether it finished. */
    boolean run(int maxCommands) throws KoboException {
        while (!kobo.isFinished() && commands < maxCommands) {
            if (!playOneCommand()) {
                throw new IllegalStateException("No bot has anything to do in " + kobo.phase());
            }
        }
        return kobo.isFinished();
    }

    private boolean playOneCommand() throws KoboException {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < bots.size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order, random);
        for (int i : order) {
            Optional<KoboBotDecision.Match> match = bots.get(i).matchFor(view(i), memories.get(i));
            if (match.isPresent()) {
                apply(i, match.get());
                return true;
            }
        }
        for (int i : order) {
            KoboBotView view = view(i);
            if (hasOwnStep(view)) {
                apply(i, bots.get(i).decide(view, memories.get(i)));
                return true;
            }
        }
        return false;
    }

    private static boolean hasOwnStep(KoboBotView view) {
        return view.actions().stream().anyMatch(a -> a != Action.MATCH_DISCARD);
    }

    private KoboBotView view(int i) {
        return KoboBotView.forSeat(kobo, new PlayerId(i));
    }

    private void apply(int i, KoboBotDecision decision) throws KoboException {
        PlayerId seat = new PlayerId(i);
        KoboEvent event;
        try {
            event = execute(seat, decision);
        } catch (KoboException e) {
            // a card looked at with a King was matched away meanwhile: the table falls back to skipping the power
            if (!(decision instanceof KoboBotDecision.KingSwap)) {
                throw e;
            }
            fallbacks++;
            event = execute(seat, new KoboBotDecision.SkipPower());
        }
        commands++;
        if (event instanceof KoboEvent.MatchPenalised) {
            penalties++;
        }
        if (event instanceof KoboEvent.MatchSucceeded) {
            matches++;
        }
        if (kobo.cardsInPlay() != 52) {
            throw new IllegalStateException("Cards in play: " + kobo.cardsInPlay() + " after " + event);
        }
        feed(event);
        observeAll();
    }

    private KoboEvent execute(PlayerId seat, KoboBotDecision decision) throws KoboException {
        if (decision instanceof KoboBotDecision.Ready) {
            return kobo.ready(seat);
        } else if (decision instanceof KoboBotDecision.Draw) {
            return kobo.draw(seat);
        } else if (decision instanceof KoboBotDecision.SwapDrawn d) {
            return kobo.swapDrawn(seat, d.slot());
        } else if (decision instanceof KoboBotDecision.DiscardDrawn) {
            return kobo.discardDrawn(seat);
        } else if (decision instanceof KoboBotDecision.Peek d) {
            return kobo.peek(seat, d.target());
        } else if (decision instanceof KoboBotDecision.BlindSwap d) {
            return kobo.blindSwap(seat, d.ownSlot(), d.other());
        } else if (decision instanceof KoboBotDecision.KingSwap d) {
            return kobo.kingSwap(seat, d.ownSlot());
        } else if (decision instanceof KoboBotDecision.SkipPower) {
            return kobo.skipPower(seat);
        } else if (decision instanceof KoboBotDecision.EndTurn) {
            return kobo.endTurn(seat);
        } else if (decision instanceof KoboBotDecision.AnnounceKobo) {
            return kobo.announceKobo(seat);
        } else if (decision instanceof KoboBotDecision.GiveTen d) {
            return kobo.giveTen(seat, d.target());
        } else if (decision instanceof KoboBotDecision.Match d) {
            return kobo.matchDiscard(seat, d.target(), d.giveSlot(), d.expectedTopRank(), d.expectedRevision());
        }
        throw new IllegalStateException("Unknown decision " + decision);
    }

    private void feed(KoboEvent event) {
        if (event instanceof KoboEvent.Swapped e) {
            memories.get(e.seat().id()).onOwnDrawnPlaced(e.slot());
        } else if (event instanceof KoboEvent.BlindSwapped e) {
            memories.forEach(m -> m.onSwapped(e.own(), e.other()));
        } else if (event instanceof KoboEvent.KingSwapped e) {
            memories.forEach(m -> m.onSwapped(e.own(), e.other()));
        } else if (event instanceof KoboEvent.MatchSucceeded e) {
            if (e.given() != null) {
                memories.forEach(m -> m.onMoved(e.given(), e.target()));
            }
        } else if (event instanceof KoboEvent.TurnEnded e) {
            endOwnTurn(e.seat());
        } else if (event instanceof KoboEvent.KoboAnnounced e) {
            endOwnTurn(e.seat());
        }
    }

    private void endOwnTurn(PlayerId seat) {
        memories.get(seat.id()).onOwnTurnEnded(random, levels.get(seat.id()).tuning().forgetProbability());
    }

    private void observeAll() {
        for (int i = 0; i < memories.size(); i++) {
            memories.get(i).observe(view(i));
        }
    }
}
