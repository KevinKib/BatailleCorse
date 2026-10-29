package org.kevinkib.cardgames.bataillecorse.domain;

import org.kevinkib.cardgames.bataillecorse.domain.penality.PutCardsUnderPile;
import org.kevinkib.cardgames.bataillecorse.domain.slaprules.SlapRules;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;
import org.kevinkib.cards.testhelpers.HandBuilder;

import java.util.List;

import static org.kevinkib.cards.testhelpers.CardBuilder.aCard;

/**
 * Builds two-player games with known hands (seats 0 and 1, seat 0 to play, default rules) and plays
 * scripted sends, so tests can reach a pile situation through the real domain methods.
 */
public final class BatailleCorseScenario {

    private BatailleCorseScenario() {
    }

    /** Hands are listed top first; the pile is empty and seat 0 plays first. */
    public static BatailleCorse game(List<FrenchRank> seat0, List<FrenchRank> seat1) {
        return game(seat0, seat1, SlapRules.DEFAULT);
    }

    public static BatailleCorse game(List<FrenchRank> seat0, List<FrenchRank> seat1, SlapRules rules) {
        return game(GameId.generate(), seat0, seat1, rules);
    }

    /** A fresh game under an existing id, as a rematch or a Play again deals one. */
    public static BatailleCorse game(GameId id, List<FrenchRank> seat0, List<FrenchRank> seat1) {
        return game(id, seat0, seat1, SlapRules.DEFAULT);
    }

    /** As {@link #game(List, List)}, every card a spade: the cards a real deck deals, a suit included. */
    public static BatailleCorse suitedGame(List<FrenchRank> seat0, List<FrenchRank> seat1) {
        return new BatailleCorse(
                GameId.generate(),
                List.of(player(0, seat0, FrenchSuit.SPADE), player(1, seat1, FrenchSuit.SPADE)),
                0,
                CentralPileBuilder.aCentralPile().withNoCards().withState(CentralPileState.NEUTRAL).build(),
                SlapRules.DEFAULT,
                new PutCardsUnderPile(2));
    }

    private static BatailleCorse game(GameId id, List<FrenchRank> seat0, List<FrenchRank> seat1, SlapRules rules) {
        return new BatailleCorse(
                id,
                List.of(player(0, seat0, null), player(1, seat1, null)),
                0,
                CentralPileBuilder.aCentralPile().withNoCards().withState(CentralPileState.NEUTRAL).build(),
                rules,
                new PutCardsUnderPile(2));
    }

    /** The current player sends, {@code sends} times in a row (so seats alternate, seat 0 first). */
    public static BatailleCorse afterSends(List<FrenchRank> seat0, List<FrenchRank> seat1, int sends) {
        BatailleCorse game = game(seat0, seat1);
        play(game, sends);
        return game;
    }

    public static void play(BatailleCorse game, int sends) {
        try {
            for (int i = 0; i < sends; i++) {
                game.send(game.getCurrentPlayer());
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Player player(int id, List<FrenchRank> ranks, FrenchSuit suit) {
        return PlayerBuilder.aPlayer().withId(id)
                .withHand(HandBuilder.aHand()
                        .withCards(ranks.stream()
                                .map(rank -> suit == null ? aCard().withRank(rank).build()
                                        : aCard().withRank(rank).withSuit(suit).build())
                                .toList())
                        .build())
                .build();
    }
}
