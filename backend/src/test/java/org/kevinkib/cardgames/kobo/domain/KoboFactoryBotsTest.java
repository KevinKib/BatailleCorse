package org.kevinkib.cardgames.kobo.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.InvalidOptionException;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** A computer seat is allowed at Kobo, and its level is a validated seat option. */
class KoboFactoryBotsTest {

    private final KoboFactory factory = new KoboFactory();

    @Test
    void theGameSupportsComputerSeats() {
        assertThat(factory.supportsBots(), is(true));
    }

    @Test
    void theOnlySeatOptionIsTheLevelWithMediumAsTheDefault() {
        assertThat(factory.seatOptionDescriptors(), is(List.of(new OptionDescriptor(KoboFactory.LEVEL_KEY,
                OptionType.CHOICE, List.of("easy", "medium", "hard"), "medium", "options.kobo.level"))));
        assertThat(KoboFactory.LEVEL_KEY, is("level"));
    }

    @Test
    void aKnownLevelIsAccepted() {
        factory.validateSeatOption("level", "hard");
    }

    @Test
    void anUnknownLevelOrOptionIsRefused() {
        assertThrows(InvalidOptionException.class, () -> factory.validateSeatOption("level", "expert"));
        assertThrows(InvalidOptionException.class, () -> factory.validateSeatOption("tier", "3"));
    }

    @Test
    void theGameOptionsAreUnchanged() {
        assertThat(factory.optionDescriptors(), is(List.of()));
    }
}
