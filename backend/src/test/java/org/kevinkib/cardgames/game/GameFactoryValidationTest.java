package org.kevinkib.cardgames.game;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameFactoryValidationTest {

    private static final OptionDescriptor SPEED = new OptionDescriptor(
            "speed", OptionType.CHOICE, List.of("slow", "fast"), "slow", "options.fake.speed");

    private static class OptionedFactory extends FakeGameFactory {
        @Override
        public List<OptionDescriptor> optionDescriptors() {
            return List.of(SPEED);
        }
    }

    @Test
    void givenFactoryWithoutDescriptors_whenOptionDescriptors_thenEmpty() {
        assertThat(new FakeGameFactory().optionDescriptors(), is(empty()));
    }

    @Test
    void givenFactoryWithoutDescriptors_whenValidateNone_thenAccepted() {
        assertThat(new FakeGameFactory().validate(GameOptions.none()), is(GameOptions.none()));
    }

    @Test
    void givenFactoryWithoutDescriptors_whenValidateAnyKey_thenRefused() {
        assertThrows(InvalidOptionException.class,
                () -> new FakeGameFactory().validate(GameOptions.of(Map.of("anything", "x"))));
    }

    @Test
    void givenDescriptor_whenValidateUnknownKey_thenRefused() {
        assertThrows(InvalidOptionException.class,
                () -> new OptionedFactory().validate(GameOptions.of(Map.of("speed", "fast", "bogus", "1"))));
    }

    @Test
    void givenDescriptor_whenValidateUnknownValue_thenRefused() {
        assertThrows(InvalidOptionException.class,
                () -> new OptionedFactory().validate(GameOptions.of(Map.of("speed", "warp"))));
    }

    @Test
    void givenDescriptor_whenValidateMissingKey_thenDefaultFilled() {
        assertThat(new OptionedFactory().validate(GameOptions.none()).values(), is(Map.of("speed", "slow")));
    }

    @Test
    void givenDescriptor_whenValidateValidValue_thenKept() {
        assertThat(new OptionedFactory().validate(GameOptions.of(Map.of("speed", "fast"))).values(),
                is(Map.of("speed", "fast")));
    }
}
