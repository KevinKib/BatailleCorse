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
import java.util.random.RandomGenerator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The lobby option {@code hostColour}: the factory turns it into the seat that plays White. */
class ThreeMenMorrisHostColourTest {

    private static final PlayerId HOST = new PlayerId(0);
    private static final PlayerId GUEST = new PlayerId(1);

    /** A generator whose bounded draws always answer the given value, counting them. */
    private static final class FixedDraw implements RandomGenerator {
        private final int value;
        int draws;

        FixedDraw(int value) {
            this.value = value;
        }

        @Override
        public int nextInt(int bound) {
            draws++;
            return value;
        }

        @Override
        public long nextLong() {
            throw new UnsupportedOperationException("the factory must draw with nextInt(bound)");
        }
    }

    private ThreeMenMorris create(RandomGenerator random, String colour) {
        return (ThreeMenMorris) new ThreeMenMorrisFactory(random)
                .create(GameId.generate(), 2, GameOptions.of(Map.of("hostColour", colour)));
    }

    @Test
    void hostColourIsAChoiceDefaultingToRandom() {
        List<OptionDescriptor> options = new ThreeMenMorrisFactory(new FixedDraw(0)).optionDescriptors();

        assertThat(options.get(1), is(new OptionDescriptor("hostColour", OptionType.CHOICE,
                List.of("white", "black", "random"), "random", "options.threeMenMorris.hostColour")));
    }

    @Test
    void whiteMakesTheHostWhiteWithoutDrawing() {
        FixedDraw random = new FixedDraw(1);

        ThreeMenMorris game = create(random, "white");

        assertThat(game.whiteSeat(), is(HOST));
        assertThat(game.currentSeat(), is(HOST));
        assertThat(random.draws, is(0));
    }

    @Test
    void blackMakesTheGuestWhiteAndTheHostWaitsToPlaceSecond() throws Exception {
        FixedDraw random = new FixedDraw(0);

        ThreeMenMorris game = create(random, "black");

        assertThat(game.whiteSeat(), is(GUEST));
        assertThat(game.currentSeat(), is(GUEST));
        assertThat(game.colourOf(HOST), is(Colour.BLACK));
        assertThat(game.legalPlacements(HOST), is(List.of()));
        assertThat(game.legalPlacements(GUEST).size(), is(9));
        assertThat(random.draws, is(0));
    }

    @Test
    void randomDrawsOnceAndFollowsTheGenerator() {
        FixedDraw hostWhite = new FixedDraw(0);
        FixedDraw hostBlack = new FixedDraw(1);

        assertThat(create(hostWhite, "random").whiteSeat(), is(HOST));
        assertThat(create(hostBlack, "random").whiteSeat(), is(GUEST));
        assertThat(hostWhite.draws, is(1));
        assertThat(hostBlack.draws, is(1));
    }

    @Test
    void noOptionsDrawTheHostColourOnce() {
        FixedDraw random = new FixedDraw(1);

        ThreeMenMorris game = (ThreeMenMorris) new ThreeMenMorrisFactory(random).create(GameId.generate(), 2);

        assertThat(game.whiteSeat(), is(GUEST));
        assertThat(random.draws, is(1));
    }

    @Test
    void validateCompletesTheDefaultAndRefusesAnUnknownColour() {
        ThreeMenMorrisFactory factory = new ThreeMenMorrisFactory(new FixedDraw(0));

        assertThat(factory.validate(GameOptions.of(Map.of("hostColour", "random"))),
                is(GameOptions.of(Map.of("variant", "tapatan", "hostColour", "random"))));
        assertThrows(InvalidOptionException.class,
                () -> factory.validate(GameOptions.of(Map.of("hostColour", "red"))));
    }

    @Test
    void anUnknownColourReachingCreateIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> create(new FixedDraw(0), "red"));
    }
}
