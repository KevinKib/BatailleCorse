package org.kevinkib.cardgames.blackjack.presentation;

import org.kevinkib.cardgames.blackjack.domain.Blackjack;
import org.kevinkib.cardgames.blackjack.domain.BlackjackFactory;
import org.kevinkib.cardgames.blackjack.domain.BlackjackOptions;
import org.kevinkib.cardgames.blackjack.domain.RandomShoeSource;
import org.kevinkib.cardgames.blackjack.domain.ScriptedShoeSource;
import org.kevinkib.cardgames.blackjack.presentation.dto.BlackjackDto;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
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
 * The Blackjack application layer wired over a real session core and a recording messaging double, with a scripted
 * shoe so a test knows every card it needs.
 */
public final class BlackjackTable {

    final ScriptedShoeSource shoes = new ScriptedShoeSource(new RandomShoeSource());
    private final SessionTable base = new SessionTable(new BlackjackFactory(shoes), new BullshitFactory());
    final InMemorySessionRepository repository = base.repository;
    final SessionService sessions = base.sessions;
    final RecordingMessaging messaging = base.messaging;
    final SeatStateBroadcaster<Blackjack, BlackjackDto> broadcaster = new SeatStateBroadcaster<>(messaging,
            sessions::botSeats, sessions::seatNames,
            (game, viewer, names, bots) -> BlackjackDto.forViewer(game, viewer, names, bots, sessions::seatOptions));
    final BlackjackGameActions actions = new BlackjackGameActions(sessions, broadcaster);
    final LobbyBroadcaster lobbyBroadcaster = new LobbyBroadcaster(messaging);
    final BlackjackWebSocketController ws = new BlackjackWebSocketController(sessions, broadcaster,
            new org.kevinkib.cardgames.presentation.command.SeatCommands(sessions, messaging), actions, messaging);
    final BlackjackRestController rest = new BlackjackRestController(sessions, broadcaster, lobbyBroadcaster);

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
        return Arrays.stream(codes.trim().split("\\s+")).map(BlackjackTable::card).toList();
    }

    /**
     * Starts a game whose hand {@code h + 1} is dealt from the scripted prefix {@code hands[h]} (deal order, then the
     * draw order). Registered with every seat claimed.
     */
    Blackjack start(int seats, GameOptions options, String... hands) {
        GameId id = GameId.generate();
        for (int h = 0; h < hands.length; h++) {
            shoes.fix(id, h + 1, cards(hands[h]));
        }
        return base.install(new Blackjack(id, seats, shoes, BlackjackOptions.from(options)),
                BlackjackFactory.GAME_TYPE);
    }

    Blackjack start(int seats, String... hands) {
        return start(seats, GameOptions.none(), hands);
    }

    String token(Blackjack game, int seat) {
        return base.token(game, seat);
    }
}
