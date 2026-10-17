package org.kevinkib.cardgames.president.domain.event;

/**
 * Why a trick ended: every other player passed, the player who takes 1st place played their last card (the owner's
 * rule), a 2 cut the trick, or four cards of a rank closed it (completed in succession, in turn or out of turn, or
 * played at once under {@code quadClears}).
 */
public enum TrickEndReason { ALL_PASSED, FIRST_OUT, CUT, QUAD }
