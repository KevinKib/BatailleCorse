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

    /** The type of every game this deployment knows. */
    public List<String> gameTypes() {
        return factories.stream().map(GameFactory::gameType).toList();
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

    public List<OptionDescriptor> seatOptionDescriptors(String gameType) {
        return factoryFor(gameType).seatOptionDescriptors();
    }

    /** Strict validation of one seat option by the game itself; see {@link GameFactory#validateSeatOption}. */
    public void validateSeatOption(String gameType, String key, String value) {
        factoryFor(gameType).validateSeatOption(key, value);
    }

    public boolean supportsBots(String gameType) {
        return factoryFor(gameType).supportsBots();
    }

    public int minPlayers(String gameType) {
        return factoryFor(gameType).minPlayers();
    }

    public int maxPlayers(String gameType) {
        return factoryFor(gameType).maxPlayers();
    }
}
