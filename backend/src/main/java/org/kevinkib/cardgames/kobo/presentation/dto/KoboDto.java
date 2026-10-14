package org.kevinkib.cardgames.kobo.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cardgames.kobo.domain.turn.Action;
import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.kevinkib.cardgames.kobo.domain.scoring.RoundResult;
import org.kevinkib.cardgames.kobo.domain.tableau.Tableau;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cards.domain.Card;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One player's view of a Kobo game, the only projection of the aggregate. Public information is the
 * same for every viewer; the private parts (starting cards, drawn card, last peek) are filled only
 * for the viewer they belong to. A face-down card's value appears nowhere else, until the round is
 * revealed.
 */
public record KoboDto(
        boolean started,
        String id,
        String gameType,
        int version,
        int round,
        String phase,
        int currentSeat,
        List<Integer> readySeats,
        Integer announcer,
        List<Integer> finalTurnsRemaining,
        String pendingPower,
        int drawPileSize,
        int discardSize,
        CardDto discardTop,
        boolean revealed,
        List<PlayerDto> players,
        boolean hasDrawn,
        CardDto drawnCard,
        List<SlotCardDto> startingCards,
        RevealDto reveal,
        List<String> availableActions,
        RoundDto lastRound,
        OutcomeDto outcome) {

    public record SlotDto(int slot, boolean occupied, int revision, CardDto card) {
    }

    public record SlotCardDto(int slot, CardDto card) {
    }

    /** {@code bot} is true for a computer seat and omitted from the JSON otherwise (additive field). */
    public record PlayerDto(int seat, String name, boolean current, int total, int handSize, List<SlotDto> slots,
                            @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean bot) {
    }

    public record RevealDto(int seat, int slot, int revision, CardDto card) {
    }

    public record PlayerRoundDto(int seat, int handValue, int roundPoints, int totalBefore, int totalAfter,
                                 boolean reset) {
    }

    public record RoundDto(List<PlayerRoundDto> players, Integer announcer, boolean announcerWon, Integer giftTo) {

        static RoundDto from(RoundResult result) {
            List<PlayerRoundDto> players = result.handValues().keySet().stream()
                    .sorted(Comparator.comparingInt(PlayerId::id))
                    .map(p -> new PlayerRoundDto(p.id(), result.handValues().get(p), result.roundPoints().get(p),
                            result.totalsBefore().get(p), result.totalsAfter().get(p), result.reset().contains(p)))
                    .toList();
            return new RoundDto(players,
                    result.announcer() == null ? null : result.announcer().id(),
                    result.announcerWon(),
                    result.giftTo() == null ? null : result.giftTo().id());
        }
    }

    public static KoboDto forViewer(Kobo game, PlayerId viewer) {
        return forViewer(game, viewer, Map.of());
    }

    public static KoboDto forViewer(Kobo game, PlayerId viewer, Map<PlayerId, String> names) {
        return forViewer(game, viewer, names, Set.of());
    }

    public static KoboDto forViewer(Kobo game, PlayerId viewer, Map<PlayerId, String> names, Set<PlayerId> bots) {
        synchronized (game) {
            Phase phase = game.phase();
            boolean showCards = game.isRevealed();
            List<PlayerDto> players = game.getPlayerIds().stream()
                    .map(p -> new PlayerDto(p.id(), names.get(p), p.equals(game.currentSeat()), game.total(p),
                            game.handSize(p), slotsOf(game, p, showCards), bots.contains(p)))
                    .toList();
            List<SlotCardDto> starting = game.startingCards(viewer).entrySet().stream()
                    .map(e -> new SlotCardDto(e.getKey(), CardDto.from(e.getValue())))
                    .toList();
            RevealDto reveal = game.revealFor(viewer)
                    .map(r -> new RevealDto(r.ref().seat().id(), r.ref().slot(), r.revision(), CardDto.from(r.card())))
                    .orElse(null);
            return new KoboDto(
                    true,
                    game.getId().uuid().toString(),
                    KoboFactory.GAME_TYPE,
                    game.version(),
                    game.round(),
                    phase.name(),
                    game.currentSeat().id(),
                    game.readySeats().stream().map(PlayerId::id).sorted().toList(),
                    game.announcer().map(PlayerId::id).orElse(null),
                    game.pendingFinalTurns().stream().map(PlayerId::id).toList(),
                    game.pendingPower().name(),
                    game.drawPileSize(),
                    game.discardSize(),
                    game.discardTop().map(CardDto::from).orElse(null),
                    showCards,
                    players,
                    game.hasDrawn(),
                    game.drawnCard(viewer).map(CardDto::from).orElse(null),
                    starting,
                    reveal,
                    game.availableActions(viewer).stream().map(Action::name).toList(),
                    game.lastRoundResult().map(RoundDto::from).orElse(null),
                    OutcomeDto.from(game));
        }
    }

    private static List<SlotDto> slotsOf(Kobo game, PlayerId seat, boolean showCards) {
        return game.slots(seat).stream()
                .map(s -> slotDto(s, showCards))
                .toList();
    }

    private static SlotDto slotDto(Tableau.Slot slot, boolean showCards) {
        Card card = showCards ? slot.card() : null;
        return new SlotDto(slot.index(), slot.occupied(), slot.revision(), CardDto.from(card));
    }
}
