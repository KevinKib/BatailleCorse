package org.kevinkib.cardgames.president.domain.event;

/** What a successful command changed, in order. Events carry no hidden information beyond what was played. */
public sealed interface PresidentEvent
        permits Played, Passed, TrickWon, PlayerOut, RoundOver,
        RoundDealt, CardsGivenBack, Ready, RoundScored, MatchFinished {
}
