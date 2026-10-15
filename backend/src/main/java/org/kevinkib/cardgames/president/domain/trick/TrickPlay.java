package org.kevinkib.cardgames.president.domain.trick;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.play.Play;

/** One play of a trick and who made it. */
public record TrickPlay(PlayerId seat, Play play) {
}
