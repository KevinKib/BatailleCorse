package org.kevinkib.cardgames.mangola.presentation.api;

import java.util.List;

/** A seat's whole draft: 16 counts in the order of its own loop (the domain checks the count and the sum). */
public record MangolaArrangePayload(String gameId, String token, List<Integer> counts) {
}
