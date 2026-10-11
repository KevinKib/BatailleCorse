package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.OptionDescriptor;

import java.util.List;

public class GameFactories {

    private final List<GameFactory> factories;

    public GameFactories(List<GameFactory> factories) {
        this.factories = factories;
    }

    public GameFactory factoryFor(String gameType) {
        return factories.stream()
                .filter(f -> f.gameType().equals(gameType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown game type " + gameType));
    }

    public List<OptionDescriptor> optionDescriptors(String gameType) {
        return factoryFor(gameType).optionDescriptors();
    }

    /** Strict validation by the game itself; see {@link GameFactory#validate}. */
    public GameOptions validate(String gameType, GameOptions options) {
        return factoryFor(gameType).validate(options);
    }

    public int minPlayers(String gameType) {
        return factoryFor(gameType).minPlayers();
    }

    public int maxPlayers(String gameType) {
        return factoryFor(gameType).maxPlayers();
    }
}
