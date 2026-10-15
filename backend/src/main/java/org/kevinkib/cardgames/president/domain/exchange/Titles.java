package org.kevinkib.cardgames.president.domain.exchange;

import java.util.ArrayList;
import java.util.List;

/** The titles by finishing place, for a given number of players. */
public final class Titles {

    private Titles() {
    }

    /**
     * Titles from the first place to the last: President, then Vice-President (from 4 players), Neutrals,
     * Vice-Scum (from 4 players) and Scum. With 3 players the middle place is the only Neutral.
     */
    public static List<Title> forPlaces(int players) {
        if (players < 3) {
            throw new IllegalArgumentException("Titles need at least 3 players, not " + players);
        }
        List<Title> titles = new ArrayList<>();
        titles.add(Title.PRESIDENT);
        if (players == 3) {
            titles.add(Title.NEUTRAL);
            titles.add(Title.SCUM);
            return List.copyOf(titles);
        }
        titles.add(Title.VICE_PRESIDENT);
        for (int i = 0; i < players - 4; i++) {
            titles.add(Title.NEUTRAL);
        }
        titles.add(Title.VICE_SCUM);
        titles.add(Title.SCUM);
        return List.copyOf(titles);
    }
}
