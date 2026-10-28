package org.kevinkib.cardgames.draughts.presentation.bot;

import java.util.function.Function;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.bot.DraughtsBot;
import org.kevinkib.cardgames.draughts.domain.bot.DraughtsBotLevel;
import org.kevinkib.cardgames.draughts.presentation.DraughtsGameActions;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.presentation.bot.BotScheduler;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.bot.BotSeats;
import org.kevinkib.cardgames.presentation.bot.ThinkingDelay;
import org.kevinkib.cardgames.presentation.dto.event.EventData;
import org.kevinkib.cardgames.presentation.state.GameStateListener;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;

/** Skeleton (red step of the TDD cycle): does nothing yet. */
public class DraughtsBotCoordinator implements GameStateListener<Draughts>, GameEvictionListener {

    public DraughtsBotCoordinator(BotSeats botSeats, BotSeatOptions seatOptions, DraughtsGameActions actions,
                                  BotScheduler scheduler, ThinkingDelay thinkingDelay,
                                  Function<DraughtsBotLevel, DraughtsBot> bots) {
    }

    @Override
    public void onBroadcast(Draughts game, String eventType, EventData eventData) {
    }

    @Override
    public void onEvicted(GameId id) {
    }
}
