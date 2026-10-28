package org.kevinkib.cardgames.ninetynine.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.ninetynine.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.ninetynine.presentation.api.NinetyNineFixedDealPayload;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * End-to-end test support, present only under the Spring {@code test} profile: fixes the first cards of a round, the
 * seat that starts round 1 and the next reshuffle of a room's game before it plays. Not mapped at all in any other
 * profile.
 */
@Profile("test")
@RestController
@RequestMapping("/api/test/ninety-nine")
public class NinetyNineTestDealController {

    private final ScriptedDeckSource decks;

    public NinetyNineTestDealController(ScriptedDeckSource decks) {
        this.decks = decks;
    }

    @PutMapping("/{gameId}/deal")
    public ResponseEntity<?> fixDeal(@PathVariable String gameId,
                                     @RequestBody(required = false) NinetyNineFixedDealPayload payload) {
        try {
            if (payload == null || (payload.cards() == null && payload.firstSeat() == null
                    && payload.reshuffle() == null)) {
                throw new IllegalArgumentException("A body with cards, firstSeat or reshuffle is required");
            }
            GameId id = new GameId(gameId);
            // parse everything first, so a bad body fixes nothing
            List<Card> cards = payload.cards() == null ? null : parseAll(payload.cards());
            List<Card> reshuffle = payload.reshuffle() == null ? null : parseAll(payload.reshuffle());
            if (payload.firstSeat() != null && payload.firstSeat() < 0) {
                throw new IllegalArgumentException("A seat is 0 or more");
            }
            if (cards != null) {
                decks.fix(id, payload.round() == null ? 1 : payload.round(), cards);
            }
            if (payload.firstSeat() != null) {
                decks.fixFirstSeat(id, payload.firstSeat());
            }
            if (reshuffle != null) {
                decks.fixReshuffle(id, reshuffle);
            }
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    @DeleteMapping("/{gameId}/deal")
    public ResponseEntity<?> clearDeal(@PathVariable String gameId) {
        try {
            decks.clear(new GameId(gameId));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid game id"));
        }
    }

    private static List<Card> parseAll(List<String> codes) {
        List<Card> cards = new ArrayList<>();
        for (String code : codes) {
            cards.add(parse(code));
        }
        return cards;
    }

    private static Card parse(String code) {
        if (code == null || code.length() < 2) {
            throw new IllegalArgumentException("Invalid card " + code);
        }
        FrenchSuit suit = switch (code.charAt(code.length() - 1)) {
            case 'H' -> FrenchSuit.HEART;
            case 'D' -> FrenchSuit.DIAMOND;
            case 'C' -> FrenchSuit.CLUB;
            case 'S' -> FrenchSuit.SPADE;
            default -> throw new IllegalArgumentException("Invalid card " + code);
        };
        FrenchRank rank = switch (code.substring(0, code.length() - 1)) {
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
            default -> throw new IllegalArgumentException("Invalid card " + code);
        };
        return new Card(rank, suit);
    }
}
