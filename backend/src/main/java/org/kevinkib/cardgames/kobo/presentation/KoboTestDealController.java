package org.kevinkib.cardgames.kobo.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.kobo.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.kobo.presentation.api.KoboFixedDealPayload;
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
 * End-to-end test support, present only under the Spring {@code test} profile: fixes the first cards of a
 * round of a room's game before it starts. Not mapped at all in any other profile.
 */
@Profile("test")
@RestController
@RequestMapping("/api/test/kobo")
public class KoboTestDealController {

    private final ScriptedDeckSource decks;

    public KoboTestDealController(ScriptedDeckSource decks) {
        this.decks = decks;
    }

    @PutMapping("/{gameId}/deal")
    public ResponseEntity<?> fixDeal(@PathVariable String gameId, @RequestBody(required = false) KoboFixedDealPayload payload) {
        try {
            if (payload == null || payload.cards() == null) {
                throw new IllegalArgumentException("A body with a list of cards is required");
            }
            GameId id = new GameId(gameId);
            List<Card> cards = new ArrayList<>();
            for (String code : payload.cards()) {
                cards.add(parse(code));
            }
            decks.fix(id, payload.round() == null ? 1 : payload.round(), cards);
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
