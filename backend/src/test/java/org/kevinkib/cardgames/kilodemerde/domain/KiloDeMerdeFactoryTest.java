package org.kevinkib.cardgames.kilodemerde.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KiloDeMerdeFactoryTest {

    private final KiloDeMerdeFactory factory = new KiloDeMerdeFactory();

    @Test
    void identityAndBounds() {
        assertEquals("kilo-de-merde", factory.gameType());
        assertEquals(3, factory.minPlayers());
        assertEquals(10, factory.maxPlayers());
        assertTrue(factory.supportsBots());
    }

    @Test
    void hostOptionIsTheNumberOfRoundsDefaultFirst() {
        assertEquals(List.of(new OptionDescriptor("rounds", OptionType.CHOICE, List.of("10", "5", "15", "20"), "10",
                "options.kiloDeMerde.rounds")), factory.optionDescriptors());
    }

    @Test
    void seatOptionIsTheBotLevelDefaultingToMedium() {
        assertEquals(List.of(new OptionDescriptor("level", OptionType.CHOICE, List.of("easy", "medium", "hard"),
                "medium", "options.kiloDeMerde.level")), factory.seatOptionDescriptors());
    }

    @Test
    void validateIsStrictAndCompletesMissingKeys() {
        assertEquals(GameOptions.of(Map.of("rounds", "10")), factory.validate(GameOptions.none()));
        assertEquals(GameOptions.of(Map.of("rounds", "5")), factory.validate(GameOptions.of(Map.of("rounds", "5"))));
        assertThrows(InvalidOptionException.class, () -> factory.validate(GameOptions.of(Map.of("rounds", "7"))));
        assertThrows(InvalidOptionException.class, () -> factory.validate(GameOptions.of(Map.of("speed", "fast"))));
    }

    @Test
    void createsAGameWithTheRequestedOptionsAndPlayers() {
        KiloDeMerde game = (KiloDeMerde) factory.create(GameId.generate(), 5,
                GameOptions.of(Map.of("rounds", "15")));

        assertEquals(15, game.roundsTotal());
        assertEquals(15, game.options().rounds().count());
        assertEquals(5, game.getPlayerIds().size());
        assertEquals(Phase.AWAITING_START, game.phase());
    }

    @Test
    void createWithoutOptionsUsesTheDefaults() {
        KiloDeMerde game = (KiloDeMerde) factory.create(GameId.generate(), 3);

        assertEquals(10, game.roundsTotal());
    }

    @Test
    void anUnknownRoundsValueFallsBackToTheDefaultOnCreation() {
        KiloDeMerde game = (KiloDeMerde) factory.create(GameId.generate(), 3,
                GameOptions.of(Map.of("rounds", "7")));

        assertEquals(10, game.roundsTotal());
    }

    @Test
    void refusesAPlayerCountOutsideTheRange() {
        assertThrows(IllegalArgumentException.class, () -> factory.create(GameId.generate(), 2));
        assertThrows(IllegalArgumentException.class, () -> factory.create(GameId.generate(), 11));
    }
}
