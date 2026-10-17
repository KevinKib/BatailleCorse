package org.kevinkib.cardgames.president.domain.rules;

/**
 * The 2 cuts (spec catalogue V4). {@code OFF}: a 2 is only the highest card. {@code SINGLE}: one 2 may be played on any
 * play, whatever its count, and closes the trick. {@code LADDER}: k twos answer a set of k+1 cards of a lower rank and
 * close the trick. A cut never answers the equal-play binding.
 */
public enum TwoCuts implements ChoiceOption { OFF, SINGLE, LADDER }
