package org.kevinkib.cardgames.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface GameFactory {

    /** Stable identifier used to select this game when creating a session. */
    String gameType();

    /** Fewest players the game can start with. */
    int minPlayers();

    /** Most players the game can seat. */
    int maxPlayers();

    Game create(GameId id, int nbPlayers);

    /** Creates the game with host-selected options. Games that ignore options inherit this default. */
    default Game create(GameId id, int nbPlayers, GameOptions options) {
        return create(id, nbPlayers);
    }

    /** The options the host may set for this game, in display order. None by default. */
    default List<OptionDescriptor> optionDescriptors() {
        return List.of();
    }

    /** The options the host may set on a computer seat (for example its tier), in display order. None by default. */
    default List<OptionDescriptor> seatOptionDescriptors() {
        return List.of();
    }

    /**
     * Strict validation of one seat option, with the same rule as the game options: the key must be declared
     * by {@link #seatOptionDescriptors()} and the value one of its values.
     *
     * @throws InvalidOptionException when the key or the value is unknown
     */
    default void validateSeatOption(String key, String value) {
        OptionDescriptor descriptor = seatOptionDescriptors().stream()
                .filter(d -> d.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new InvalidOptionException("Unknown seat option " + key));
        if (!descriptor.values().contains(value)) {
            throw new InvalidOptionException("Invalid value " + value + " for seat option " + key);
        }
    }

    /**
     * Strict validation of a full option map: a key absent from {@link #optionDescriptors()} or a value
     * outside its descriptor's values is refused, a missing key takes its default. Returns the completed
     * options.
     *
     * @throws InvalidOptionException when the map holds an unknown key or an unknown value
     */
    default GameOptions validate(GameOptions requested) {
        List<OptionDescriptor> descriptors = optionDescriptors();
        for (String key : requested.values().keySet()) {
            if (descriptors.stream().noneMatch(d -> d.key().equals(key))) {
                throw new InvalidOptionException("Unknown option " + key);
            }
        }
        Map<String, String> completed = new LinkedHashMap<>();
        for (OptionDescriptor descriptor : descriptors) {
            String value = requested.get(descriptor.key()).orElse(descriptor.defaultValue());
            if (!descriptor.values().contains(value)) {
                throw new InvalidOptionException("Invalid value " + value + " for option " + descriptor.key());
            }
            completed.put(descriptor.key(), value);
        }
        return GameOptions.of(completed);
    }
}
