package org.kevinkib.cardgames.kobo.presentation.dto.event;

import org.kevinkib.cardgames.kobo.domain.KoboEvent;
import org.kevinkib.cardgames.kobo.presentation.dto.CardDto;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

/**
 * Public description of what a command did. Never carries a face-down card: a peek names the looked
 * at position but not the card; only cards that land on the public discard pile are shown.
 * {@code action} is the command ({@link KoboEventType}) even when the broadcast event is a
 * ROUND_END or GAME_OVER caused by it.
 */
public record KoboEventData(
        int seat,
        String action,
        Integer slot,
        Integer targetSeat,
        Integer targetSlot,
        CardDto card,
        Integer givenSlot,
        Boolean success,
        Boolean barrierOpened) implements EventData {

    private static KoboEventData of(int seat, KoboEventType action) {
        return new KoboEventData(seat, action.name(), null, null, null, null, null, null, null);
    }

    public static KoboEventData from(KoboEvent event) {
        if (event instanceof KoboEvent.Ready e) {
            return new KoboEventData(e.seat().id(), KoboEventType.READY.name(),
                    null, null, null, null, null, null, e.barrierOpened());
        }
        if (event instanceof KoboEvent.Drawn e) {
            return of(e.seat().id(), KoboEventType.DRAW);
        }
        if (event instanceof KoboEvent.DrawSkipped e) {
            return new KoboEventData(e.seat().id(), KoboEventType.DRAW.name(),
                    null, null, null, null, null, false, null);
        }
        if (event instanceof KoboEvent.Swapped e) {
            return new KoboEventData(e.seat().id(), KoboEventType.SWAP.name(),
                    e.slot(), null, null, CardDto.from(e.discarded()), null, null, null);
        }
        if (event instanceof KoboEvent.DiscardedDrawn e) {
            return new KoboEventData(e.seat().id(), KoboEventType.DISCARD_DRAWN.name(),
                    null, null, null, CardDto.from(e.card()), null, null, null);
        }
        if (event instanceof KoboEvent.Peeked e) {
            return new KoboEventData(e.seat().id(), KoboEventType.PEEK.name(),
                    null, e.target().seat().id(), e.target().slot(), null, null, null, null);
        }
        if (event instanceof KoboEvent.BlindSwapped e) {
            return new KoboEventData(e.seat().id(), KoboEventType.BLIND_SWAP.name(),
                    e.own().slot(), e.other().seat().id(), e.other().slot(), null, null, null, null);
        }
        if (event instanceof KoboEvent.KingSwapped e) {
            return new KoboEventData(e.seat().id(), KoboEventType.KING_SWAP.name(),
                    e.own().slot(), e.other().seat().id(), e.other().slot(), null, null, null, null);
        }
        if (event instanceof KoboEvent.PowerSkipped e) {
            return of(e.seat().id(), KoboEventType.SKIP_POWER);
        }
        if (event instanceof KoboEvent.TurnEnded e) {
            return of(e.seat().id(), KoboEventType.END_TURN);
        }
        if (event instanceof KoboEvent.KoboAnnounced e) {
            return of(e.seat().id(), KoboEventType.KOBO);
        }
        if (event instanceof KoboEvent.MatchSucceeded e) {
            return new KoboEventData(e.seat().id(), KoboEventType.MATCH.name(),
                    null, e.target().seat().id(), e.target().slot(), CardDto.from(e.card()),
                    e.given() == null ? null : e.given().slot(), true, null);
        }
        if (event instanceof KoboEvent.MatchPenalised e) {
            return new KoboEventData(e.seat().id(), KoboEventType.MATCH.name(),
                    null, null, null, null, null, false, null);
        }
        if (event instanceof KoboEvent.TenGiven e) {
            return new KoboEventData(e.from().id(), KoboEventType.GIVE_TEN.name(),
                    null, e.to().id(), null, null, null, null, null);
        }
        throw new IllegalArgumentException("Unknown event " + event);
    }

    public static KoboEventType typeOf(KoboEvent event) {
        if (event instanceof KoboEvent.Ready e) {
            return KoboEventType.READY;
        }
        if (event instanceof KoboEvent.Drawn e) {
            return KoboEventType.DRAW;
        }
        if (event instanceof KoboEvent.DrawSkipped e) {
            return KoboEventType.DRAW;
        }
        if (event instanceof KoboEvent.Swapped e) {
            return KoboEventType.SWAP;
        }
        if (event instanceof KoboEvent.DiscardedDrawn e) {
            return KoboEventType.DISCARD_DRAWN;
        }
        if (event instanceof KoboEvent.Peeked e) {
            return KoboEventType.PEEK;
        }
        if (event instanceof KoboEvent.BlindSwapped e) {
            return KoboEventType.BLIND_SWAP;
        }
        if (event instanceof KoboEvent.KingSwapped e) {
            return KoboEventType.KING_SWAP;
        }
        if (event instanceof KoboEvent.PowerSkipped e) {
            return KoboEventType.SKIP_POWER;
        }
        if (event instanceof KoboEvent.TurnEnded e) {
            return KoboEventType.END_TURN;
        }
        if (event instanceof KoboEvent.KoboAnnounced e) {
            return KoboEventType.KOBO;
        }
        if (event instanceof KoboEvent.MatchSucceeded e) {
            return KoboEventType.MATCH;
        }
        if (event instanceof KoboEvent.MatchPenalised e) {
            return KoboEventType.MATCH;
        }
        if (event instanceof KoboEvent.TenGiven e) {
            return KoboEventType.GIVE_TEN;
        }
        throw new IllegalArgumentException("Unknown event " + event);
    }
}
