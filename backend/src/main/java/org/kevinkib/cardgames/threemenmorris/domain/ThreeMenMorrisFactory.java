package org.kevinkib.cardgames.threemenmorris.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.Arrays;
import java.util.List;

public class ThreeMenMorrisFactory implements GameFactory {

    public static final String GAME_TYPE = "three-men-morris";
    public static final String VARIANT_KEY = "variant";

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public int minPlayers() {
        return ThreeMenMorris.PLAYERS;
    }

    @Override
    public int maxPlayers() {
        return ThreeMenMorris.PLAYERS;
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return create(id, nbPlayers, GameOptions.none());
    }

    @Override
    public Game create(GameId id, int nbPlayers, GameOptions options) {
        if (nbPlayers != ThreeMenMorris.PLAYERS) {
            throw new IllegalArgumentException("Three Men's Morris is played by exactly "
                    + ThreeMenMorris.PLAYERS + " players, not " + nbPlayers);
        }
        MorrisVariant variant = options.get(VARIANT_KEY).map(MorrisVariant::fromKey).orElse(MorrisVariant.DEFAULT);
        return new ThreeMenMorris(id, variant.rules(), new PlayerId(0));
    }

    @Override
    public List<OptionDescriptor> optionDescriptors() {
        return List.of(new OptionDescriptor(VARIANT_KEY, OptionType.CHOICE,
                Arrays.stream(MorrisVariant.values()).map(MorrisVariant::key).toList(),
                MorrisVariant.DEFAULT.key(), "options.threeMenMorris.variant"));
    }
}
