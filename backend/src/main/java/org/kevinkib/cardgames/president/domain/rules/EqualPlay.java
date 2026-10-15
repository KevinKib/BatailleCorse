package org.kevinkib.cardgames.president.domain.rules;

/**
 * V2 (PR V1): equal play, with its binding, the four closing the trick and the completion out of turn. Until
 * V1 ships the domain behaves as {@link #OFF}.
 */
public enum EqualPlay implements ChoiceOption { ON, OFF }
