package org.kevinkib.cardgames.draughts.presentation.api;

/** Test seam body: 50 layout characters ({@code w W b B .}, square 1 first) and the colour to move. */
public record DraughtsPositionPayload(String layout, String toMove) {
}
