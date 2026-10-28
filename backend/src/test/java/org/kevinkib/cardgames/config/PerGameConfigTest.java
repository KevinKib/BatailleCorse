package org.kevinkib.cardgames.config;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Each game declares its own beans in its own configuration class; adding a game never edits AppConfig. */
@SpringBootTest
class PerGameConfigTest {

    private static final List<String> CONFIGS = List.of("BatailleCorseConfig", "BullshitConfig", "KoboConfig",
            "ThreeMenMorrisConfig", "MangolaConfig", "PresidentConfig", "DraughtsConfig", "NinetyNineConfig",
            "KiloDeMerdeConfig", "BlackjackConfig");
    private static final List<String> GAME_TYPES = List.of("bataille-corse", "bullshit", "kobo",
            "three-men-morris", "mangola", "president", "draughts", "ninety-nine", "kilo-de-merde", "blackjack");

    @Autowired ApplicationContext context;
    @Autowired GameFactories gameFactories;
    @Autowired List<GameFactory> factories;
    @Autowired List<GameLifecycleBroadcaster> broadcasters;

    @Test
    void everyGameHasItsOwnConfigurationClassInTheContext() throws Exception {
        for (String name : CONFIGS) {
            Class<?> type = Class.forName("org.kevinkib.cardgames.config." + name);
            assertEquals(1, context.getBeansOfType(type).size(), name);
        }
    }

    @Test
    void gameFactoriesKnowsEveryGameType() {
        for (String type : GAME_TYPES) {
            assertEquals(type, gameFactories.factoryFor(type).gameType());
        }
        assertEquals(10, factories.size());
    }

    @Test
    void everyGameHasALifecycleBroadcaster() {
        assertEquals(10, broadcasters.size());
    }
}
