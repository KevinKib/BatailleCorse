package org.kevinkib.cardgames.ninetynine.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NinetyNineFactoryTest {

    private final NinetyNineFactory factory = new NinetyNineFactory();

    @Test
    void identityAndBounds() {
        assertEquals("ninety-nine", factory.gameType());
        assertEquals(2, factory.minPlayers());
        assertEquals(12, factory.maxPlayers());
        assertTrue(factory.supportsBots());
    }

    @Test
    void hostOptionsAreLivesThenQuickDefaultFirst() {
        assertEquals(List.of(
                new OptionDescriptor("lives", OptionType.CHOICE, List.of("3", "2", "4"), "3",
                        "options.ninetyNine.lives"),
                new OptionDescriptor("quick", OptionType.CHOICE, List.of("off", "on"), "off",
                        "options.ninetyNine.quick")),
                factory.optionDescriptors());
    }

    @Test
    void seatOptionIsTheBotLevelDefaultingToMedium() {
        assertEquals(List.of(new OptionDescriptor("level", OptionType.CHOICE, List.of("easy", "medium", "hard"),
                "medium", "options.ninetyNine.level")), factory.seatOptionDescriptors());
    }

    @Test
    void validateIsStrictAndCompletesMissingKeys() {
        assertEquals(GameOptions.of(Map.of("lives", "3", "quick", "off")), factory.validate(GameOptions.none()));
        assertEquals(GameOptions.of(Map.of("lives", "2", "quick", "off")),
                factory.validate(GameOptions.of(Map.of("lives", "2"))));
        assertThrows(InvalidOptionException.class, () -> factory.validate(GameOptions.of(Map.of("lives", "5"))));
        assertThrows(InvalidOptionException.class, () -> factory.validate(GameOptions.of(Map.of("speed", "fast"))));
    }

    @Test
    void createsAGameWithTheRequestedOptions() {
        NinetyNine game = (NinetyNine) factory.create(GameId.generate(), 4,
                GameOptions.of(Map.of("lives", "2", "quick", "on")));

        assertEquals(2, game.options().lives().count());
        assertEquals(NinetyNineOptions.QuickMode.ON, game.options().quick());
        assertEquals(4, game.getPlayerIds().size());
    }

    @Test
    void createsAGameWithDefaultOptions() {
        assertInstanceOf(NinetyNine.class, factory.create(GameId.generate(), 2));
        assertEquals(NinetyNineOptions.defaults(),
                ((NinetyNine) factory.create(GameId.generate(), 2)).options());
    }
}
