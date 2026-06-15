package org.kevinkib.cardgames.bullshit.presentation.dto;

import org.kevinkib.cardgames.bullshit.domain.player.Player;

public record BullshitPlayerDto(String id, int handCount, boolean isCurrentPlayer) {

    public static BullshitPlayerDto from(Player player, boolean isCurrentPlayer) {
        return new BullshitPlayerDto(String.valueOf(player.id().id()), player.handSize(), isCurrentPlayer);
    }
}
