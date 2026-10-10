package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

/** What a command did, for the presentation layer. None of these carries a hidden card. */
public sealed interface KoboEvent {

    record Ready(PlayerId seat, boolean barrierOpened) implements KoboEvent { }

    record Drawn(PlayerId seat) implements KoboEvent { }

    record DrawSkipped(PlayerId seat) implements KoboEvent { }

    record Swapped(PlayerId seat, int slot, Card discarded) implements KoboEvent { }

    record DiscardedDrawn(PlayerId seat, Card card, Power power) implements KoboEvent { }

    record Peeked(PlayerId seat, SlotRef target) implements KoboEvent { }

    record BlindSwapped(PlayerId seat, SlotRef own, SlotRef other) implements KoboEvent { }

    record KingSwapped(PlayerId seat, SlotRef own, SlotRef other) implements KoboEvent { }

    record PowerSkipped(PlayerId seat) implements KoboEvent { }

    record TurnEnded(PlayerId seat) implements KoboEvent { }

    record KoboAnnounced(PlayerId seat) implements KoboEvent { }

    record MatchSucceeded(PlayerId seat, SlotRef target, Card card, SlotRef given) implements KoboEvent { }

    record MatchPenalised(PlayerId seat, boolean cardGiven) implements KoboEvent { }

    record TenGiven(PlayerId from, PlayerId to) implements KoboEvent { }
}
