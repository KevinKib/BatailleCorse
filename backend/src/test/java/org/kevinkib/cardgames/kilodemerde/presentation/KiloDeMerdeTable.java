package org.kevinkib.cardgames.kilodemerde.presentation;

import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeFactory;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeOptions;
import org.kevinkib.cardgames.kilodemerde.domain.RandomDeckSource;
import org.kevinkib.cardgames.kilodemerde.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.KiloDeMerdeDto;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.presentation.support.SessionTable;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;

/**
 * The Kilo de merde application layer wired over a real session core and a recording messaging double, with a
 * scripted deck source so a test knows every hand and the order of the kilo pile.
 */
public final class KiloDeMerdeTable {

    /** Three seats, no four of a kind anywhere: a slap now is a false slap. */
    public static final String NO_SET = "KS KH KD QC / JS JH QD QH / KC JD JC QS";
    /** Four seats, no four of a kind anywhere. */
    public static final String NO_SET_4 = "KS KH KD 2C / JS JH QD QH / KC JD JC QS / 2S 2H 2D QC";
    /** Three seats, each holding a four of a kind: any slap is valid. */
    public static final String ALL_SETS = "KS KH KD KC / JS JH JD JC / QS QH QD QC";
    /** The top card first; every card is in the kilo pile of a three-seat game. */
    public static final String PILE = "AS 9C 5D 8H 7D";

    final ScriptedDeckSource decks = new ScriptedDeckSource(new RandomDeckSource());
    private final SessionTable base = new SessionTable(new KiloDeMerdeFactory(decks), new BullshitFactory());

    final InMemorySessionRepository repository = base.repository;
    final SessionService sessions = base.sessions;
    final RecordingMessaging messaging = base.messaging;
    final KiloDeMerdeDeadlines deadlines;
    final SeatStateBroadcaster<KiloDeMerde, KiloDeMerdeDto> broadcaster;
    final KiloDeMerdeGameActions actions;
    final LobbyBroadcaster lobbyBroadcaster = new LobbyBroadcaster(messaging);
    final KiloDeMerdeWebSocketController ws;
    final KiloDeMerdeRestController rest;

    public KiloDeMerdeTable() {
        this(KiloDeMerdeDeadlines.off());
    }

    public KiloDeMerdeTable(KiloDeMerdeDeadlines deadlines) {
        this.deadlines = deadlines;
        this.broadcaster = new SeatStateBroadcaster<>(messaging, sessions::botSeats, sessions::seatNames,
                (game, viewer, names, bots) -> KiloDeMerdeDto.forViewer(game, viewer, names, bots,
                        sessions::seatOptions, deadlines));
        this.actions = new KiloDeMerdeGameActions(sessions, broadcaster);
        this.ws = new KiloDeMerdeWebSocketController(sessions, broadcaster, new SeatCommands(sessions, messaging),
                actions);
        this.rest = new KiloDeMerdeRestController(sessions, broadcaster, lobbyBroadcaster);
    }

    /** "KS" is the king of spades, "10H" the ten of hearts, "AD" the ace of diamonds. */
    public static Card card(String code) {
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

    public static List<Card> cards(String codes) {
        return codes.isBlank() ? List.of() : Arrays.stream(codes.trim().split("\\s+")).map(KiloDeMerdeTable::card).toList();
    }

    /** Hands separated by a slash, in seat order: {@code "KS KH KD QC / JS JH QD QH / KC JD JC QS"}. */
    public static List<List<Card>> hands(String hands) {
        return Arrays.stream(hands.split("/")).map(KiloDeMerdeTable::cards).toList();
    }

    /**
     * Starts a game whose first deal gives the seats {@code hands} (a slash between seats) and whose kilo pile is
     * {@code pile}, top first. The game is registered with every seat claimed.
     */
    KiloDeMerde start(int seats, GameOptions options, String hands, String pile) {
        GameId id = GameId.generate();
        decks.fixDeal(id, hands(hands), cards(pile));
        return base.install(new KiloDeMerde(id, seats, decks, KiloDeMerdeOptions.from(options)),
                KiloDeMerdeFactory.GAME_TYPE);
    }

    KiloDeMerde start(int seats, String hands, String pile) {
        return start(seats, GameOptions.none(), hands, pile);
    }

    /** Three seats and the standard pile. */
    KiloDeMerde start(String hands) {
        return start(3, hands, PILE);
    }

    String token(KiloDeMerde game, int seat) {
        return base.token(game, seat);
    }

    static Clock fixedClock(long epochMs) {
        return Clock.fixed(java.time.Instant.ofEpochMilli(epochMs), java.time.ZoneOffset.UTC);
    }
}
