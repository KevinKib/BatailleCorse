package org.kevinkib.cardgames.kilodemerde.presentation;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.kilodemerde.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.kilodemerde.presentation.api.KiloDeMerdeFixedDealPayload;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cardgames.presentation.dto.CardLabels;
import org.kevinkib.cards.domain.Card;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * End-to-end test support, present only under the Spring {@code test} profile: fixes the leading hands of the next
 * deal and the kilo pile of a room's game before it starts. Not mapped at all in any other profile.
 */
@Profile("test")
@RestController
@RequestMapping("/api/test/kilo-de-merde")
public class KiloDeMerdeTestDealController {

    private final ScriptedDeckSource decks;

    public KiloDeMerdeTestDealController(ScriptedDeckSource decks) {
        this.decks = decks;
    }

    @PutMapping("/{gameId}/deal")
    public ResponseEntity<?> fixDeal(@PathVariable String gameId,
                                     @RequestBody(required = false) KiloDeMerdeFixedDealPayload payload) {
        try {
            if (payload == null || (payload.hands() == null && payload.pile() == null)) {
                throw new IllegalArgumentException("A body with hands or pile is required");
            }
            GameId id = new GameId(gameId);
            // parse everything first, so a bad body fixes nothing
            List<List<Card>> hands = payload.hands() == null ? List.of()
                    : payload.hands().stream().map(KiloDeMerdeTestDealController::parseAll).toList();
            List<Card> pile = payload.pile() == null ? List.of() : parseAll(payload.pile());
            decks.fixDeal(id, hands, pile);
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

    private static List<Card> parseAll(List<CardDto> dtos) {
        if (dtos == null) {
            throw new IllegalArgumentException("Missing hand");
        }
        return dtos.stream().map(dto -> {
            if (dto == null) {
                throw new IllegalArgumentException("Missing card");
            }
            return CardLabels.card(dto);
        }).toList();
    }
}
