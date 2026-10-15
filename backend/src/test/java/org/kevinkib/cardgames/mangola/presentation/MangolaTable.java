package org.kevinkib.cardgames.mangola.presentation;

import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaBoard;
import org.kevinkib.cardgames.mangola.domain.MangolaFactory;
import org.kevinkib.cardgames.mangola.domain.MangolaGeometry;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.presentation.dto.MangolaDto;
import org.kevinkib.cardgames.presentation.LobbyBroadcaster;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.presentation.support.SessionTable;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.util.Arrays;
import java.util.random.RandomGenerator;

/** The Mangola application layer wired over a real session core and a recording messaging double. */
final class MangolaTable {

    static final PlayerId SEAT_0 = new PlayerId(0);
    static final PlayerId SEAT_1 = new PlayerId(1);

    /** Every draw is 0: the host starts, and a random set-up drops all 32 seeds into the first pit of the loop. */
    static final RandomGenerator FIXED = new RandomGenerator() {
        @Override
        public long nextLong() {
            return 0;
        }

        @Override
        public int nextInt(int bound) {
            return 0;
        }
    };

    private final SessionTable base = new SessionTable(new MangolaFactory(FIXED), new BullshitFactory());

    final InMemorySessionRepository repository = base.repository;
    final SessionService sessions = base.sessions;
    final RecordingMessaging messaging = base.messaging;
    final SeatStateBroadcaster<Mangola, MangolaDto> broadcaster =
            new SeatStateBroadcaster<>(messaging, BotSeats.none(), sessions::seatNames,
                    (game, viewer, names, bots) -> MangolaDto.forViewer(game, viewer, names));
    final MangolaGameActions actions = new MangolaGameActions(sessions, broadcaster);
    final LobbyBroadcaster lobbyBroadcaster = new LobbyBroadcaster(messaging);
    final MangolaWebSocketController ws =
            new MangolaWebSocketController(sessions, broadcaster, new SeatCommands(sessions, messaging), actions);
    final MangolaRestController rest = new MangolaRestController(sessions, broadcaster, lobbyBroadcaster);

    /** Registers a started game with both seats claimed (one token per seat). */
    Mangola install(Mangola game) {
        return base.install(game, MangolaFactory.GAME_TYPE);
    }

    /** A game in the placement phase, seat 0 owning side A (rows 1 and 2) and starting. */
    Mangola placementGame() {
        return install(new Mangola(GameId.generate(), MangolaRules.OWNER, SEAT_0));
    }

    /** A game in play on the given board, seat 0 owning side A and to move. */
    Mangola playGame(MangolaBoard board) {
        return install(Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0, board));
    }

    String token(Mangola game, int seat) {
        return base.token(game, seat);
    }

    // ---- fixtures

    /** A board from "pit:seeds" tokens such as {@code "a2:2 c2:1"}; pits not listed are empty. */
    static MangolaBoard board(String spec) {
        MangolaBoard board = MangolaBoard.empty();
        for (String token : spec.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                String[] parts = token.split(":");
                board = board.with(MangolaGeometry.pit(parts[0]), Integer.parseInt(parts[1]));
            }
        }
        return board;
    }

    /** Two seeds on each of the 16 pits. */
    static int[] twoPerPit() {
        int[] counts = new int[MangolaGeometry.LOOP_LENGTH];
        Arrays.fill(counts, 2);
        return counts;
    }

    /** All 32 seeds on the first pit of the loop. */
    static int[] allOnFirst() {
        int[] counts = new int[MangolaGeometry.LOOP_LENGTH];
        counts[0] = 32;
        return counts;
    }
}
