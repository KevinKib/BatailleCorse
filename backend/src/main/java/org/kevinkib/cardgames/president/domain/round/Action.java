package org.kevinkib.cardgames.president.domain.round;

/**
 * What a seat may do at a given moment. {@code COMPLETE} plays the cards that complete the four, in turn or out of
 * turn: it may be the only action of a seat whose turn it is not.
 */
public enum Action { PLAY, PASS, COMPLETE, GIVE_BACK, READY, SEE_EXCHANGE }
