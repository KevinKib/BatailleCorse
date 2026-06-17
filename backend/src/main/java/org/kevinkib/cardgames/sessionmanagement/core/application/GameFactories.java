package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.game.GameFactory;

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

    public int minPlayers(String gameType) {
        return factoryFor(gameType).minPlayers();
    }

    public int maxPlayers(String gameType) {
        return factoryFor(gameType).maxPlayers();
    }
}
