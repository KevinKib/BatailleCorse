package org.kevinkib.cardgames.kilodemerde.domain.event;

/** What a successful command changed, in order. Hands are revealed only by {@link RoundOver}. */
public sealed interface KiloDeMerdeEvent
        permits RoundDealt, PassChosen, StepResolved, RaceOpened, Slapped, FalseSlap, KiloDrawn, Flushed, RoundOver,
        GameFinished, Forfeited {
}
