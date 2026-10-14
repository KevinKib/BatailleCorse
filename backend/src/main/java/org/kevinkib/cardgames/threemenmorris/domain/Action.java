package org.kevinkib.cardgames.threemenmorris.domain;

/** What a seat may do right now: PLACE and MOVE on its turn, the draw actions at any time they apply. */
public enum Action {
    PLACE, MOVE, OFFER_DRAW, ACCEPT_DRAW, DECLINE_DRAW
}
