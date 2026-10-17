package org.kevinkib.cardgames.president.domain.rules;

/**
 * Going out on a top-rank card (spec catalogue V7). {@code LAST_PLACE}: a player whose last play holds a 2 goes out but
 * is placed last, below the other players, the later forbidden finisher lower. {@code OFF}: no restriction.
 */
public enum ForbiddenFinish implements ChoiceOption { LAST_PLACE, OFF }
