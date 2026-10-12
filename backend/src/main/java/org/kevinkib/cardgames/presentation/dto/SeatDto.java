package org.kevinkib.cardgames.presentation.dto;

import java.util.Map;

/** One seat as a table shows it: the options of a computer seat (its tier), none for a human. */
public record SeatDto(int id, String name, boolean joined, boolean bot, Map<String, String> seatOptions) {
}
