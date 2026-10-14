package org.kevinkib.cardgames.kobo.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.event.PrivateReveal;
import org.kevinkib.cardgames.kobo.domain.power.Power;
import org.kevinkib.cardgames.kobo.domain.scoring.RoundResult;
import org.kevinkib.cardgames.kobo.domain.tableau.Tableau;
import org.kevinkib.cardgames.kobo.domain.turn.Action;
import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.kevinkib.cards.domain.Card;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What a human sitting at one seat sees of a Kobo game: the same information as the seat's client view
 * ({@code KoboDto.forViewer}), in domain types. A face-down card appears nowhere except in the seat's own
 * private parts (starting cards, drawn card, last peek); every slot card is {@code null} until the round is
 * revealed. A bot strategy receives this view, never the {@link Kobo} aggregate.
 */
public record KoboBotView(
        PlayerId seat,
        int version,
        int round,
        Phase phase,
        PlayerId currentSeat,
        List<Action> actions,
        Optional<Card> discardTop,
        int discardSize,
        int drawPileSize,
        Power pendingPower,
        Optional<PlayerId> announcer,
        List<PlayerId> pendingFinalTurns,
        Set<PlayerId> readySeats,
        boolean revealed,
        Map<PlayerId, Integer> totals,
        Map<PlayerId, List<SlotView>> tableaux,
        boolean hasDrawn,
        Map<Integer, Card> startingCards,
        Optional<Card> drawn,
        Optional<PrivateReveal> reveal,
        Optional<RoundResult> lastRound) {

    /** One position of a tableau; {@code card} is null unless the round is revealed or the slot is empty. */
    public record SlotView(int slot, boolean occupied, int revision, Card card) {
    }

    /** Built under the game's monitor, so the fields belong to one version. */
    public static KoboBotView forSeat(Kobo game, PlayerId seat) {
        synchronized (game) {
            boolean showCards = game.isRevealed();
            Map<PlayerId, List<SlotView>> tableaux = new LinkedHashMap<>();
            Map<PlayerId, Integer> totals = new LinkedHashMap<>();
            for (PlayerId player : game.getPlayerIds()) {
                tableaux.put(player, game.slots(player).stream().map(s -> slotView(s, showCards)).toList());
                totals.put(player, game.total(player));
            }
            return new KoboBotView(
                    seat,
                    game.version(),
                    game.round(),
                    game.phase(),
                    game.currentSeat(),
                    List.copyOf(game.availableActions(seat)),
                    game.discardTop(),
                    game.discardSize(),
                    game.drawPileSize(),
                    game.pendingPower(),
                    game.announcer(),
                    game.pendingFinalTurns(),
                    game.readySeats(),
                    showCards,
                    totals,
                    tableaux,
                    game.hasDrawn(),
                    game.startingCards(seat),
                    game.drawnCard(seat),
                    game.revealFor(seat),
                    game.lastRoundResult());
        }
    }

    private static SlotView slotView(Tableau.Slot slot, boolean showCards) {
        return new SlotView(slot.index(), slot.occupied(), slot.revision(), showCards ? slot.card() : null);
    }

    public boolean can(Action action) {
        return actions.contains(action);
    }

    public boolean finished() {
        return phase == Phase.FINISHED;
    }

    public List<PlayerId> seats() {
        return List.copyOf(tableaux.keySet());
    }

    public List<PlayerId> opponents() {
        return tableaux.keySet().stream().filter(p -> !p.equals(seat)).toList();
    }

    public List<SlotView> slots(PlayerId player) {
        return tableaux.get(player);
    }

    public int handSize(PlayerId player) {
        return (int) slots(player).stream().filter(SlotView::occupied).count();
    }
}
