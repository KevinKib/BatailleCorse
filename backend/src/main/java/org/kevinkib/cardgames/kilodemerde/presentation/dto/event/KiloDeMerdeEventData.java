package org.kevinkib.cardgames.kilodemerde.presentation.dto.event;

import org.kevinkib.cardgames.kilodemerde.domain.event.FalseSlap;
import org.kevinkib.cardgames.kilodemerde.domain.event.Flushed;
import org.kevinkib.cardgames.kilodemerde.domain.event.GameFinished;
import org.kevinkib.cardgames.kilodemerde.domain.event.KiloDeMerdeEvent;
import org.kevinkib.cardgames.kilodemerde.domain.event.KiloDrawn;
import org.kevinkib.cardgames.kilodemerde.domain.event.PassChosen;
import org.kevinkib.cardgames.kilodemerde.domain.event.RaceOpened;
import org.kevinkib.cardgames.kilodemerde.domain.event.ReadyMarked;
import org.kevinkib.cardgames.kilodemerde.domain.event.RoundDealt;
import org.kevinkib.cardgames.kilodemerde.domain.event.RoundOver;
import org.kevinkib.cardgames.kilodemerde.domain.event.Slapped;
import org.kevinkib.cardgames.kilodemerde.domain.event.StepResolved;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.KiloDeMerdeDto;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

/**
 * Public description of one thing that happened at the table. Only the fields of the event type are set, the others
 * are null. A PASS_CHOSEN has {@code seat} (the mark, never the card); a STEP the {@code round} and the new
 * {@code step}; a RACE {@code seat} (the first slapper); a SLAP {@code seat} and its {@code rank} in the slap order;
 * a FALSE_SLAP {@code seat}; a DRAW {@code seat}, the {@code card} (null when the pile was empty) and {@code
 * kilosAfter}; a FLUSH {@code seat} and {@code returnedCount} (the cards that went back into the pile, the 7
 * included); a ROUND_START the {@code round}; a ROUND_OVER the {@code outcome} with every hand; a GAME_OVER the
 * {@code result}. Nothing private is in it: the cards a seat receives travel in its own state only.
 */
public record KiloDeMerdeEventData(
        Integer seat,
        Integer round,
        Integer step,
        Integer rank,
        CardDto card,
        Integer kilosAfter,
        Integer returnedCount,
        KiloDeMerdeDto.OutcomeDto outcome,
        KiloDeMerdeDto.ResultDto result) implements EventData {

    public static KiloDeMerdeEventData from(KiloDeMerdeEvent event) {
        if (event instanceof RoundDealt e) {
            return new KiloDeMerdeEventData(null, e.round(), null, null, null, null, null, null, null);
        } else if (event instanceof ReadyMarked e) {
            return new KiloDeMerdeEventData(e.seat().id(), null, null, null, null, null, null, null, null);
        } else if (event instanceof PassChosen e) {
            return new KiloDeMerdeEventData(e.seat().id(), null, null, null, null, null, null, null, null);
        } else if (event instanceof StepResolved e) {
            return new KiloDeMerdeEventData(null, e.round(), e.step(), null, null, null, null, null, null);
        } else if (event instanceof RaceOpened e) {
            return new KiloDeMerdeEventData(e.seat().id(), null, null, null, null, null, null, null, null);
        } else if (event instanceof Slapped e) {
            return new KiloDeMerdeEventData(e.seat().id(), null, null, e.rank(), null, null, null, null, null);
        } else if (event instanceof FalseSlap e) {
            return new KiloDeMerdeEventData(e.seat().id(), null, null, null, null, null, null, null, null);
        } else if (event instanceof KiloDrawn e) {
            return new KiloDeMerdeEventData(e.seat().id(), null, null, null, CardDto.from(e.card()), e.kilosAfter(),
                    null, null, null);
        } else if (event instanceof Flushed e) {
            return new KiloDeMerdeEventData(e.seat().id(), null, null, null, null, null, e.returnedCount(), null,
                    null);
        } else if (event instanceof RoundOver e) {
            return new KiloDeMerdeEventData(null, null, null, null, null, null, null,
                    KiloDeMerdeDto.outcomeOf(e.outcome()), null);
        } else if (event instanceof GameFinished e) {
            return new KiloDeMerdeEventData(null, null, null, null, null, null, null, null,
                    KiloDeMerdeDto.resultOf(e.result()));
        }
        return new KiloDeMerdeEventData(null, null, null, null, null, null, null, null, null);
    }
}
