package org.kevinkib.cardgames.blackjack.domain;

/** The action is not in the seat's available actions (a double or a split that the hand or the stack forbids). */
public class ActionNotAllowedException extends BlackjackException {

    public ActionNotAllowedException(Action action) {
        super("The action " + action + " is not allowed now");
    }
}
