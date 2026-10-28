package org.kevinkib.cardgames.ninetynine.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.CardEffect;
import org.kevinkib.cardgames.ninetynine.domain.Direction;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNineOptions;
import org.kevinkib.cardgames.ninetynine.domain.Phase;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Hand-written and random {@link NinetyNineBotView}s for the bot tests. The bot is always seat 0 and on turn. */
final class NinetyNineBotViews {

    static final PlayerId ME = new PlayerId(0);

    private NinetyNineBotViews() {
    }

    /** "KH" is the king of hearts, "10S" the ten of spades, "AD" the ace of diamonds. */
    static Card c(String code) {
        String rank = code.substring(0, code.length() - 1);
        FrenchRank frenchRank = switch (rank) {
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
        FrenchSuit suit = switch (code.charAt(code.length() - 1)) {
            case 'H' -> FrenchSuit.HEART;
            case 'D' -> FrenchSuit.DIAMOND;
            case 'C' -> FrenchSuit.CLUB;
            case 'S' -> FrenchSuit.SPADE;
            default -> throw new IllegalArgumentException(code);
        };
        return new Card(frenchRank, suit);
    }

    static List<Card> cards(String codes) {
        return codes.isBlank() ? List.of()
                : Arrays.stream(codes.trim().split("\\s+")).map(NinetyNineBotViews::c).toList();
    }

    static List<Card> fullDeck() {
        List<Card> deck = new ArrayList<>();
        for (FrenchRank rank : FrenchRank.values()) {
            if (rank == FrenchRank.JOKER) {
                continue;
            }
            for (FrenchSuit suit : FrenchSuit.getSuits()) {
                deck.add(new Card(rank, suit));
            }
        }
        return deck;
    }

    /** A view for seat 0, on turn, with `seats` live seats of three lives and three cards each. */
    static NinetyNineBotView view(int seats, int total, String hand, String pile, int stockSize) {
        return view(seats, total, cards(hand), cards(pile), stockSize, Direction.CLOCKWISE);
    }

    static NinetyNineBotView view(int seats, int total, List<Card> hand, List<Card> pile, int stockSize,
                                  Direction direction) {
        Map<PlayerId, Integer> handSizes = new LinkedHashMap<>();
        Map<PlayerId, Integer> lives = new LinkedHashMap<>();
        List<PlayerId> live = new ArrayList<>();
        for (int i = 0; i < seats; i++) {
            PlayerId seat = new PlayerId(i);
            live.add(seat);
            handSizes.put(seat, i == 0 ? hand.size() : 3);
            lives.put(seat, 3);
        }
        return new NinetyNineBotView(ME, 1, 1, Phase.PLAYING, ME, total, direction, hand, pile, handSizes, lives,
                live, stockSize, NinetyNineOptions.defaults());
    }

    /** A consistent random view where the bot holds at least one safe card. */
    static NinetyNineBotView randomView(Random random) {
        while (true) {
            int seats = 2 + random.nextInt(11);
            List<Card> deck = fullDeck();
            Collections.shuffle(deck, random);
            List<Card> hand = new ArrayList<>(deck.subList(0, 3));
            int free = 52 - 3 * seats;
            int pileSize = random.nextInt(Math.min(20, free) + 1);
            List<Card> pile = new ArrayList<>(deck.subList(3, 3 + pileSize));
            int stock = free - pileSize;
            int total = random.nextBoolean() ? 80 + random.nextInt(20) : random.nextInt(100);
            if (CardEffect.safeCards(total, hand).isEmpty()) {
                continue;
            }
            Direction direction = random.nextBoolean() ? Direction.CLOCKWISE : Direction.COUNTER_CLOCKWISE;
            return view(seats, total, hand, pile, stock, direction);
        }
    }
}
