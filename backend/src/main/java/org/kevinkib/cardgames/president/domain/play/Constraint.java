package org.kevinkib.cardgames.president.domain.play;

/**
 * What the next play of a trick must answer: {@code FREE} on an empty trick (any set leads), {@code BEAT} a higher
 * play of the same count (or, with equal play on, an equal one), {@code MATCH_OR_PASS} the binding that follows an
 * equal play: the same rank and the same count, or a pass.
 */
public enum Constraint { FREE, BEAT, MATCH_OR_PASS }
