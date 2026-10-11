package org.kevinkib.cardgames.bataillecorse.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.InvalidOptionException;
import org.kevinkib.cardgames.game.OptionDescriptor;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatailleCorseFactoryTest {

    @Test
    void givenId_whenCreate_thenReturnsPlayableGameWithThatId() {
        GameId id = GameId.generate();

        Game game = new BatailleCorseFactory().create(id, 2);

        assertThat(game.getId(), is(id));
        assertThat(game.getPlayerIds(), hasSize(2));
        assertThat(game.isFinished(), is(false));
    }

    @Test
    void declaresTheTierOfAComputerSeatFromZeroToEightDefaultingToOne() {
        List<OptionDescriptor> descriptors = new BatailleCorseFactory().seatOptionDescriptors();

        assertThat(descriptors, hasSize(1));
        OptionDescriptor tier = descriptors.get(0);
        assertThat(tier.key(), is("tier"));
        assertThat(tier.values(), is(List.of("0", "1", "2", "3", "4", "5", "6", "7", "8")));
        assertThat(tier.defaultValue(), is("1"));
    }

    @Test
    void validatesATierAndRefusesAnythingElse() {
        BatailleCorseFactory factory = new BatailleCorseFactory();

        assertDoesNotThrow(() -> factory.validateSeatOption("tier", "8"));
        assertThrows(InvalidOptionException.class, () -> factory.validateSeatOption("tier", "9"));
        assertThrows(InvalidOptionException.class, () -> factory.validateSeatOption("speed", "1"));
    }
}
