package org.kevinkib.cardgames.bullshit.presentation.dto;

import org.kevinkib.cardgames.bullshit.domain.Action;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.domain.player.Player;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;
import java.util.Optional;

public record BullshitDto(
        String id,
        String gameType,
        List<CardDto> myHand,
        List<String> availableActions,
        List<BullshitPlayerDto> players,
        ClaimTargetDto currentTarget,
        int discardPileSize,
        TableDto table,
        PendingWinnerDto pendingWinner,
        OutcomeDto outcome) {

    public static BullshitDto forViewer(Bullshit game, PlayerId viewer) {
        Optional<Player> viewerPlayer = game.getPlayers().stream()
                .filter(p -> p.id().equals(viewer))
                .findFirst();

        List<CardDto> myHand = viewerPlayer
                .map(p -> p.getCards().stream().map(CardDto::from).toList())
                .orElseGet(List::of);

        boolean viewerPresent = viewerPlayer.isPresent();
        List<String> availableActions = viewerPresent
                ? game.getAvailableActions(viewer).stream().map(Action::name).toList()
                : List.of();

        PlayerId currentPlayerId = game.getCurrentPlayer().id();
        List<BullshitPlayerDto> players = game.getPlayers().stream()
                .map(p -> BullshitPlayerDto.from(p, p.id().equals(currentPlayerId)))
                .toList();

        return new BullshitDto(
                game.getId().uuid().toString(),
                BullshitFactory.GAME_TYPE,
                myHand,
                availableActions,
                players,
                ClaimTargetDto.from(game.getCurrentTarget()),
                game.getDiscardPileSize(),
                TableDto.from(game),
                PendingWinnerDto.from(game),
                OutcomeDto.from(game));
    }
}
