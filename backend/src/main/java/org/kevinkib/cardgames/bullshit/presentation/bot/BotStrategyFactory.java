package org.kevinkib.cardgames.bullshit.presentation.bot;

import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.bot.BotStrategy;

/** Port: picks the strategy bots use in a given game (the strategy depends on the game's claim mode). */
@FunctionalInterface
public interface BotStrategyFactory {

    BotStrategy forGame(Bullshit game);
}
