package org.kevinkib.cardgames.president.domain.event;

/**
 * Why a trick ended: every other player passed, the first player out of the round played their last card (the
 * owner's rule), or the four cards of a rank were completed in succession (in turn or out of turn). Cuts and the
 * quad played at once come with the variant PRs.
 */
public enum TrickEndReason { ALL_PASSED, FIRST_OUT, QUAD }
