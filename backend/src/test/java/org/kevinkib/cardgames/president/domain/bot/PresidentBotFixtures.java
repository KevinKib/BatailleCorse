package org.kevinkib.cardgames.president.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.exchange.PendingReturn;
import org.kevinkib.cardgames.president.domain.play.LegalPlays;
import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.domain.trick.TrickView;
import org.kevinkib.cards.domain.Card;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.code;

/** Scenarios for the bot tests: tables built from explicit hands, and hand-made views for the phases a table cannot reach quickly. */
final class PresidentBotFixtures {

    static final PlayerId BOT_LEADING = new PlayerId(0);
    static final PlayerId BOT_FOLLOWING = new PlayerId(1);

    private PresidentBotFixtures() {
    }

    /** The bot (seat 0) leads an empty trick; the two other seats hold one card each (3S and 4S, avoid them). */
    static PresidentBotView leading(String botHand, PresidentOptions options) {
        President game = aPresident().withHand(botHand).withHand("3S").withHand("4S").leader(0)
                .options(options).build();
        return PresidentBotView.forSeat(game, BOT_LEADING);
    }

    static PresidentBotView leading(String botHand) {
        return leading(botHand, PresidentOptions.defaults());
    }

    /** Seat 0 led {@code top}; the bot (seat 1) must answer. Seats 0 and 2 keep one other card (3S, 4S). */
    static PresidentBotView following(String botHand, String top, PresidentOptions options) {
        President game = aPresident().withHand(top + " 3S").withHand(botHand).withHand("4S").leader(0)
                .options(options).build();
        try {
            game.play(new PlayerId(0), cards(top));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return PresidentBotView.forSeat(game, BOT_FOLLOWING);
    }

    static PresidentBotView following(String botHand, String top) {
        return following(botHand, top, PresidentOptions.defaults());
    }

    /** A bot (seat 0) that owes {@code count} cards back to seat 1 in the exchange of round 2. */
    static PresidentBotView givingBack(String botHand, int count) {
        PlayerId bot = new PlayerId(0);
        PlayerId scum = new PlayerId(1);
        List<Card> hand = cards(botHand);
        return new PresidentBotView(bot, 5, 2, Phase.EXCHANGE, bot, List.of(Action.GIVE_BACK), hand,
                Map.of(bot, hand.size(), scum, hand.size() - count), new TrickView(bot, List.of(), List.of()),
                List.of(), List.of(), Map.of(), List.of(new PendingReturn(bot, scum, count, false)),
                List.of(), List.of(), Set.of(), Map.of(bot, 0, scum, 0), PresidentOptions.defaults());
    }

    /** The barrier between two rounds, the bot not ready yet. */
    static PresidentBotView atTheBarrier() {
        PlayerId bot = new PlayerId(0);
        PlayerId other = new PlayerId(1);
        return new PresidentBotView(bot, 9, 1, Phase.ROUND_OVER, bot, List.of(Action.READY), List.of(),
                Map.of(bot, 0, other, 3), new TrickView(bot, List.of(), List.of()), List.of(),
                List.of(bot), Map.of(), List.of(), List.of(), List.of(), Set.of(), Map.of(bot, 2, other, 0),
                PresidentOptions.defaults());
    }

    /** The legal plays of a hand against a top, for views built by hand. */
    static List<org.kevinkib.cardgames.president.domain.play.Play> legal(List<Card> hand,
            org.kevinkib.cardgames.president.domain.play.Play top) {
        return LegalPlays.of(hand, top, RankOrder.standard());
    }

    /** The ranks of the cards, space separated, in order: "6 6 K". */
    static String ranks(List<Card> cards) {
        return String.join(" ", cards.stream().map(c -> {
            String code = code(c);
            return code.substring(0, code.length() - 1);
        }).toList());
    }
}
