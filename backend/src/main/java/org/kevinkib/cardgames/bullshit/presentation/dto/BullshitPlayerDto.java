package org.kevinkib.cardgames.bullshit.presentation.dto;

import org.kevinkib.cardgames.bullshit.domain.player.Player;

/**
 * {@code name} is the display name the seat chose, or null when it has none (the client then falls back to
 * "Player N"); a bot never carries a name, the client labels it from {@code bot}.
 */
public record BullshitPlayerDto(String id, int handCount, boolean isCurrentPlayer, String name, boolean bot) {

    public static BullshitPlayerDto from(Player player, boolean isCurrentPlayer) {
        return from(player, isCurrentPlayer, null, false);
    }

    public static BullshitPlayerDto from(Player player, boolean isCurrentPlayer, String name, boolean bot) {
        String clean = (bot || name == null || name.isBlank()) ? null : name.trim();
        return new BullshitPlayerDto(String.valueOf(player.id().id()), player.handSize(), isCurrentPlayer, clean, bot);
    }
}
