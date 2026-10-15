package org.kevinkib.cardgames.president.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.exchange.Title;
import org.kevinkib.cardgames.president.domain.exchange.Titles;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.domain.round.Round;
import org.kevinkib.cardgames.president.domain.rules.FirstLead;
import org.kevinkib.cardgames.president.domain.rules.PresidentException;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.domain.trick.TrickView;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A President game. P1 plays a single round: the deal, tricks of sets climbing in rank, passing, going out
 * and the finishing order with its titles (match, exchange and scoring come with P2). All state changes go
 * through synchronized methods, and a rejected command throws a {@link PresidentException} and changes
 * nothing (the version does not move).
 */
public class President implements Game {

    public static final int MIN_PLAYERS = 3;
    public static final int MAX_PLAYERS = 8;

    private final GameId id;
    private final PresidentOptions options;
    private final int players;
    private final Round round;
    private int number = 1;
    private int version;

    public President(GameId id, int nbPlayers) {
        this(id, nbPlayers, new Random().nextLong());
    }

    public President(GameId id, int nbPlayers, long seed) {
        this(id, nbPlayers, seed, new RandomDeckSource());
    }

    public President(GameId id, int nbPlayers, long seed, DeckSource deckSource) {
        this(id, nbPlayers, seed, deckSource, PresidentOptions.defaults());
    }

    public President(GameId id, int nbPlayers, long seed, DeckSource deckSource, PresidentOptions options) {
        if (nbPlayers < MIN_PLAYERS || nbPlayers > MAX_PLAYERS) {
            throw new IllegalArgumentException("President is played by 3 to 8 players, not " + nbPlayers);
        }
        this.id = id;
        this.options = options;
        this.players = nbPlayers;
        DeckSpec spec = DeckSpec.standard();
        List<Card> deck = deckSource.deck(id, number, seed, spec);
        if (deck.size() != spec.size()) {
            throw new IllegalArgumentException("A deck holds " + spec.size() + " cards, not " + deck.size());
        }
        int dealer = nbPlayers - 1;
        List<List<Card>> hands = deal(deck, nbPlayers, dealer);
        int leader = firstLead(hands, options.firstLead(), new Random(seed));
        this.round = new Round(options, hands, dealer, leader);
    }

    /** Test constructor: explicit hands (any sizes) and the seat that leads. */
    President(GameId id, PresidentOptions options, long seed, List<List<Card>> hands, int leader) {
        this.id = id;
        this.options = options;
        this.players = hands.size();
        this.round = new Round(options, hands, players - 1, leader);
    }

    /** One card at a time from the seat after the dealer: the extra cards go to the seats right after them. */
    private static List<List<Card>> deal(List<Card> deck, int nbPlayers, int dealer) {
        List<List<Card>> hands = new ArrayList<>();
        for (int i = 0; i < nbPlayers; i++) {
            hands.add(new ArrayList<>());
        }
        for (int i = 0; i < deck.size(); i++) {
            hands.get((dealer + 1 + i) % nbPlayers).add(deck.get(i));
        }
        return hands;
    }

    private static int firstLead(List<List<Card>> hands, FirstLead rule, Random random) {
        return switch (rule) {
            case LOWEST_CLUB -> holderOf(hands, new Card(FrenchRank.THREE, FrenchSuit.CLUB));
            case QUEEN_OF_HEARTS -> holderOf(hands, new Card(FrenchRank.QUEEN, FrenchSuit.HEART));
            case RANDOM -> random.nextInt(hands.size());
        };
    }

    /** The first holder of the card; the seat after the dealer when nobody holds it (cannot happen with a full deck). */
    private static int holderOf(List<List<Card>> hands, Card card) {
        for (int seat = 0; seat < hands.size(); seat++) {
            if (hands.get(seat).contains(card)) {
                return seat;
            }
        }
        return 0;
    }

    // ----- commands -----

    public synchronized List<PresidentEvent> play(PlayerId seat, List<Card> cards) throws PresidentException {
        List<PresidentEvent> events = round.play(seat, cards);
        version++;
        return events;
    }

    public synchronized List<PresidentEvent> pass(PlayerId seat) throws PresidentException {
        List<PresidentEvent> events = round.pass(seat);
        version++;
        return events;
    }

    /** Forfeit belongs to the match (P2); a single round has nothing to drop a player from yet. */
    @Override
    public synchronized void forfeit(PlayerId loser) {
        throw new UnsupportedOperationException("Forfeit arrives with the match (P2)");
    }

    // ----- queries -----

    @Override
    public GameId getId() {
        return id;
    }

    /** Until P2 adds the match, a game is one round: it is finished when the round is over. */
    @Override
    public synchronized boolean isFinished() {
        return round.phase() == Phase.ROUND_OVER;
    }

    @Override
    public List<PlayerId> getPlayerIds() {
        List<PlayerId> ids = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            ids.add(new PlayerId(i));
        }
        return ids;
    }

    public PresidentOptions options() {
        return options;
    }

    public synchronized int version() {
        return version;
    }

    public synchronized int round() {
        return number;
    }

    public synchronized Phase phase() {
        return round.phase();
    }

    public synchronized PlayerId dealer() {
        return round.dealer();
    }

    public synchronized PlayerId currentSeat() {
        return round.currentSeat();
    }

    /** The cards of a seat, from the weakest to the strongest. */
    public synchronized List<Card> hand(PlayerId seat) {
        return round.hand(seat);
    }

    public synchronized List<Integer> handSizes() {
        return round.handSizes();
    }

    public synchronized TrickView trick() {
        return round.trick();
    }

    /** The cards of the tricks already won, set aside face down. */
    public synchronized List<Card> discard() {
        return round.discard();
    }

    /** The players who are out, best place first; complete (every player) once the round is over. */
    public synchronized List<PlayerId> finishingOrder() {
        return round.finishingOrder();
    }

    /** Every legal play of the seat to act; nothing for any other seat. */
    public synchronized List<Play> legalPlays(PlayerId seat) {
        return round.legalPlays(seat);
    }

    /** The titles earned by the finishing order of the finished round; empty while it is being played. */
    public synchronized Map<PlayerId, Title> roundTitles() {
        Map<PlayerId, Title> titles = new LinkedHashMap<>();
        if (round.phase() != Phase.ROUND_OVER) {
            return titles;
        }
        List<PlayerId> order = round.finishingOrder();
        List<Title> byPlace = Titles.forPlaces(players);
        for (int place = 0; place < order.size(); place++) {
            titles.put(order.get(place), byPlace.get(place));
        }
        return titles;
    }
}
