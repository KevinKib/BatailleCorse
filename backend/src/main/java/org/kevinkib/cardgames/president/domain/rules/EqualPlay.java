package org.kevinkib.cardgames.president.domain.rules;

/**
 * Equal play (spec 3.1.1, catalogue row V2): a play of the same rank and count is legal and binds the next player
 * to match it or pass, the four cards of a rank played in succession close the trick, and the holder of the
 * missing cards may complete them out of turn. {@code ON} by default (the owner's rules).
 */
public enum EqualPlay implements ChoiceOption { ON, OFF }
