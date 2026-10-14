package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.InvalidOptionException;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A computer seat is allowed, and its level is a validated seat option. */
class ThreeMenMorrisFactoryBotsTest {

    private final ThreeMenMorrisFactory factory = new ThreeMenMorrisFactory();

    @Test
    void theGameSupportsComputerSeats() {
        assertThat(factory.supportsBots(), is(true));
    }

    @Test
    void theOnlySeatOptionIsTheLevelWithGreedyAsTheDefault() {
        assertThat(factory.seatOptionDescriptors(), is(List.of(new OptionDescriptor("level", OptionType.CHOICE,
                List.of("random", "greedy", "perfect"), "greedy", "options.threeMenMorris.level"))));
    }

    @Test
    void aKnownLevelIsAccepted() {
        factory.validateSeatOption("level", "perfect");
    }

    @Test
    void anUnknownLevelOrOptionIsRefused() {
        assertThrows(InvalidOptionException.class, () -> factory.validateSeatOption("level", "grandmaster"));
        assertThrows(InvalidOptionException.class, () -> factory.validateSeatOption("tier", "3"));
    }

    @Test
    void theGameOptionsAreUnchanged() {
        assertThat(factory.optionDescriptors().stream().map(OptionDescriptor::key).toList(),
                is(List.of("variant", "hostColour")));
    }
}
