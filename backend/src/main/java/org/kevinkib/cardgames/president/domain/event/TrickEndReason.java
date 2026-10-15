package org.kevinkib.cardgames.president.domain.event;

/**
 * Why a trick ended: every other player passed, or the first player out of the round played their last card
 * (the owner's rule). Cuts, clears and the four of a rank come with the variant PRs.
 */
public enum TrickEndReason { ALL_PASSED, FIRST_OUT }
