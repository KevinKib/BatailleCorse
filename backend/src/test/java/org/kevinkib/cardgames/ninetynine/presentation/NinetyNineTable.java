package org.kevinkib.cardgames.ninetynine.presentation;

import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNineFactory;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNineOptions;
import org.kevinkib.cardgames.ninetynine.domain.RandomDeckSource;
import org.kevinkib.cardgames.ninetynine.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.ninetynine.presentation.dto.NinetyNineDto;
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

import java.util.Arrays;
import java.util.List;

/**
 * The 99 application layer wired over a real session core and a recording messaging double, with a scripted deck
 * so a test knows every hand and every draw it needs.
 */
public final class NinetyNineTable {

    final ScriptedDeckSource decks = new ScriptedDeckSource(new RandomDeckSource());
    private final SessionTable base = new SessionTable(new NinetyNineFactory(decks), new BullshitFactory());

    final InMemorySessionRepository repository = base.repository;
    final SessionService sessions = base.sessions;
    final RecordingMessaging messaging = base.messaging;
    final SeatStateBroadcaster<NinetyNine, NinetyNineDto> broadcaster = new SeatStateBroadcaster<>(messaging,
            sessions::botSeats, sessions::seatNames,
            (game, viewer, names, bots) -> NinetyNineDto.forViewer(game, viewer, names, bots, sessions::seatOptions));
    final NinetyNineGameActions actions = new NinetyNineGameActions(sessions, broadcaster);
    final LobbyBroadcaster lobbyBroadcaster = new LobbyBroadcaster(messaging);
    final NinetyNineWebSocketController ws = new NinetyNineWebSocketController(sessions, broadcaster,
            new SeatCommands(sessions, messaging), actions);
    final NinetyNineRestController rest = new NinetyNineRestController(sessions, broadcaster, lobbyBroadcaster);

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
        return Arrays.stream(codes.trim().split("\\s+")).map(NinetyNineTable::card).toList();
    }

    /**
     * Starts a game whose round {@code r + 1} opens with the cards of {@code rounds[r]}: the deal in seat order
     * (one card per seat, three times), then the stock from its top. Seat 0 starts round 1. The game is registered
     * with every seat claimed.
     */
    NinetyNine start(int seats, GameOptions options, String... rounds) {
        GameId id = GameId.generate();
        for (int r = 0; r < rounds.length; r++) {
            decks.fix(id, r + 1, cards(rounds[r]));
        }
        decks.fixFirstSeat(id, 0);
        return base.install(new NinetyNine(id, seats, decks, NinetyNineOptions.from(options)),
                NinetyNineFactory.GAME_TYPE);
    }

    NinetyNine start(int seats, String... rounds) {
        return start(seats, GameOptions.none(), rounds);
    }

    /**
     * Two seats, then the plays K (seat 0), 10 (seat 1), 5 (seat 0): total 94, seat 1 to play 9H 7H 6D, seat 0 holds
     * AS 3C 2C. Messages sent so far are forgotten.
     */
    NinetyNine atNinetyFour() throws Exception {
        NinetyNine game = start(2, "KS 10H 5C 9H AS 7H 3C 6D 2C");
        actions.play(game.getId(), new PlayerId(0), card("KS"), null);
        actions.play(game.getId(), new PlayerId(1), card("10H"), null);
        actions.play(game.getId(), new PlayerId(0), card("5C"), null);
        messaging.clear();
        return game;
    }

    /**
     * Three seats, nothing played: seat 0 holds KS 9S 4S, seat 1 7H 8H 2H, seat 2 JC QC 3C. When seat 0 plays the K
     * the total is 99 and seat 1 is stuck: it must play a card (7H) which busts it.
     */
    NinetyNine beforeABust() {
        return start(3, "KS 7H JC 9S 8H QC 4S 2H 3C 5D");
    }

    String token(NinetyNine game, int seat) {
        return base.token(game, seat);
    }
}
