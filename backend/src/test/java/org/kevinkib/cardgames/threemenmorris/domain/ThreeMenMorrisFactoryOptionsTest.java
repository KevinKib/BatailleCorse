package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The lobby option {@code variant} and the rules the factory builds from it. */
class ThreeMenMorrisFactoryOptionsTest {

    private final ThreeMenMorrisFactory factory = new ThreeMenMorrisFactory();

    private ThreeMenMorris create(GameOptions options) {
        return (ThreeMenMorris) factory.create(GameId.generate(), 2, options);
    }

    @Test
    void theOptionsAreTheVariantDefaultingToTapatanThenTheHostColour() {
        assertThat(factory.optionDescriptors(), is(List.of(new OptionDescriptor("variant", OptionType.CHOICE,
                List.of("tapatan", "achi", "picaria"), "tapatan", "options.threeMenMorris.variant"),
                new OptionDescriptor("hostColour", OptionType.CHOICE,
                        List.of("white", "black", "random"), "white", "options.threeMenMorris.hostColour"))));
        assertThat(factory.seatOptionDescriptors(), is(List.of()));
    }

    @Test
    void noOptionsOrTheDefaultBuildTapatanWithSeatZeroWhite() {
        assertThat(create(GameOptions.none()).rules(), is(MorrisRules.TAPATAN));
        assertThat(create(GameOptions.of(Map.of("variant", "tapatan"))).rules(), is(MorrisRules.TAPATAN));
        assertThat(((ThreeMenMorris) factory.create(GameId.generate(), 2)).rules(), is(MorrisRules.TAPATAN));
        assertThat(create(GameOptions.none()).whiteSeat(), is(new PlayerId(0)));
    }

    @Test
    void achiAndPicariaBuildTheirRules() {
        ThreeMenMorris achi = create(GameOptions.of(Map.of("variant", "achi")));
        ThreeMenMorris picaria = create(GameOptions.of(Map.of("variant", "picaria")));

        assertThat(achi.rules(), is(MorrisRules.ACHI));
        assertThat(achi.piecesToPlace(new PlayerId(0)), is(4));
        assertThat(picaria.rules(), is(MorrisRules.PICARIA));
        assertThat(picaria.piecesToPlace(new PlayerId(1)), is(3));
    }

    @Test
    void validateCompletesTheDefaultAndRefusesUnknownKeysAndValues() {
        assertThat(factory.validate(GameOptions.none()), is(GameOptions.of(Map.of("variant", "tapatan", "hostColour", "white"))));
        assertThat(factory.validate(GameOptions.of(Map.of("variant", "achi"))),
                is(GameOptions.of(Map.of("variant", "achi", "hostColour", "white"))));
        assertThrows(InvalidOptionException.class,
                () -> factory.validate(GameOptions.of(Map.of("variant", "nine"))));
        assertThrows(InvalidOptionException.class,
                () -> factory.validate(GameOptions.of(Map.of("colour", "white"))));
    }

    @Test
    void anUnknownVariantReachingCreateIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> create(GameOptions.of(Map.of("variant", "nine"))));
    }
}
