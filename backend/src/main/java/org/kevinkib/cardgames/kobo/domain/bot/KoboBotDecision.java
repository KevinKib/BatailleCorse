package org.kevinkib.cardgames.kobo.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.tableau.SlotRef;
import org.kevinkib.cards.domain.Rank;

/** What a bot wants to do; each case maps to one Kobo command (the caller applies it). */
public sealed interface KoboBotDecision {

    record Ready() implements KoboBotDecision { }

    record Draw() implements KoboBotDecision { }

    record SwapDrawn(int slot) implements KoboBotDecision { }

    record DiscardDrawn() implements KoboBotDecision { }

    record Peek(SlotRef target) implements KoboBotDecision { }

    record BlindSwap(int ownSlot, SlotRef other) implements KoboBotDecision { }

    record KingSwap(int ownSlot) implements KoboBotDecision { }

    record SkipPower() implements KoboBotDecision { }

    record EndTurn() implements KoboBotDecision { }

    record AnnounceKobo() implements KoboBotDecision { }

    record GiveTen(PlayerId target) implements KoboBotDecision { }

    /**
     * A matching discard. {@code giveSlot} is the own card given when the target is an opponent's (null when the
     * target is the bot's own or the bot has no card left); the expected values are the rank and the slot revision
     * the bot saw, so a stale attempt is rejected by the game without penalty.
     */
    record Match(SlotRef target, Integer giveSlot, Rank expectedTopRank, int expectedRevision)
            implements KoboBotDecision { }
}
