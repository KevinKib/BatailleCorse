package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.event.KoboEvent;
import org.kevinkib.cardgames.kobo.domain.rules.KoboException;
import org.kevinkib.cardgames.kobo.domain.tableau.SlotRef;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.Rank;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.Arrays;
import java.util.List;

public final class KoboFixtures {

    private KoboFixtures() {
    }

    /** "7H", "10S", "KD", "AC", "JH", "QS": rank then suit letter (H, D, C, S). */
    public static Card card(String code) {
        String rank = code.substring(0, code.length() - 1);
        FrenchSuit suit = switch (code.charAt(code.length() - 1)) {
            case 'H' -> FrenchSuit.HEART;
            case 'D' -> FrenchSuit.DIAMOND;
            case 'C' -> FrenchSuit.CLUB;
            case 'S' -> FrenchSuit.SPADE;
            default -> throw new IllegalArgumentException(code);
        };
        FrenchRank r = switch (rank) {
            case "A" -> FrenchRank.ACE;
            case "2" -> FrenchRank.TWO;
            case "3" -> FrenchRank.THREE;
            case "4" -> FrenchRank.FOUR;
            case "5" -> FrenchRank.FIVE;
            case "6" -> FrenchRank.SIX;
            case "7" -> FrenchRank.SEVEN;
            case "8" -> FrenchRank.EIGHT;
            case "9" -> FrenchRank.NINE;
            case "10" -> FrenchRank.TEN;
            case "J" -> FrenchRank.JACK;
            case "Q" -> FrenchRank.QUEEN;
            case "K" -> FrenchRank.KING;
            default -> throw new IllegalArgumentException(code);
        };
        return new Card(r, suit);
    }

    /**
     * A matching-discard attempt carrying the current revision of the targeted slot, as a client that
     * is up to date would send it (0 when the slot does not exist: the domain then rejects it).
     */
    public static KoboEvent match(Kobo kobo, PlayerId seat, SlotRef target, Integer giveSlot, Rank expectedTopRank)
            throws KoboException {
        int revision = 0;
        try {
            revision = kobo.slots(target.seat()).get(target.slot()).revision();
        } catch (RuntimeException e) {
            // unknown seat or slot: let the aggregate reject it
        }
        return kobo.matchDiscard(seat, target, giveSlot, expectedTopRank, revision);
    }

    public static List<Card> cards(String... codes) {
        return Arrays.stream(codes).map(KoboFixtures::card).toList();
    }
}
