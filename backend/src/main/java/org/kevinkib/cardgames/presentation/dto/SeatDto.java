package org.kevinkib.cardgames.presentation.dto;

import java.util.Map;

/**
 * One seat as a table shows it: the options of a computer seat (its tier), none for a human. {@code name} is what
 * the occupant typed, null when none (a bot never has one): the client labels such a seat in the player's language.
 */
public record SeatDto(int id, String name, boolean joined, boolean bot, Map<String, String> seatOptions) {
}
