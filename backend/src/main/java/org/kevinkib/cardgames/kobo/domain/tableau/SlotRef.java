package org.kevinkib.cardgames.kobo.domain.tableau;

import org.kevinkib.cardgames.game.PlayerId;

/** A card position on the table: a player's tableau and a slot index in it. */
public record SlotRef(PlayerId seat, int slot) {
}
