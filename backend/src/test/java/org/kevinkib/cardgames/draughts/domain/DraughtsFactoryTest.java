package org.kevinkib.cardgames.draughts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.BLACK;
import static org.kevinkib.cardgames.draughts.domain.DraughtsGames.WHITE;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;
import org.kevinkib.cardgames.game.OptionDescriptor;

class DraughtsFactoryTest {

    private static RandomGenerator constant(int value, AtomicInteger draws) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return value;
            }

            @Override
            public int nextInt(int bound) {
                draws.incrementAndGet();
                return value;
            }
        };
    }

    private final DraughtsFactory factory = new DraughtsFactory(constant(0, new AtomicInteger()));

    private Draughts create(Map<String, String> options) {
        return (Draughts) factory.create(GameId.generate(), 2, GameOptions.of(options));
    }

    @Test
    void itIsATwoPlayerGameWithBots() {
        assertThat(factory.gameType()).isEqualTo("draughts");
        assertThat(DraughtsFactory.GAME_TYPE).isEqualTo("draughts");
        assertThat(factory.minPlayers()).isEqualTo(2);
        assertThat(factory.maxPlayers()).isEqualTo(2);
        assertThat(factory.supportsBots()).isTrue();
    }

    @Test
    void anyOtherNumberOfPlayersIsRefused() {
        assertThatThrownBy(() -> factory.create(GameId.generate(), 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.create(GameId.generate(), 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theDefaultsAreARandomHostColourAndOffersOn() {
        Draughts game = (Draughts) factory.create(GameId.generate(), 2);

        assertThat(game.whiteSeat()).isEqualTo(WHITE);
        assertThat(game.drawOffersAllowed()).isTrue();
        assertThat(game.position()).isEqualTo(DraughtsPosition.initial());
    }

    @Test
    void theHostColourIsChosenOrDrawnOnce() {
        assertThat(create(Map.of("hostColour", "white")).whiteSeat()).isEqualTo(WHITE);
        assertThat(create(Map.of("hostColour", "black")).whiteSeat()).isEqualTo(BLACK);

        AtomicInteger draws = new AtomicInteger();
        DraughtsFactory drawing = new DraughtsFactory(constant(1, draws));
        Draughts game = (Draughts) drawing.create(GameId.generate(), 2, GameOptions.of(Map.of("hostColour", "random")));
        assertThat(game.whiteSeat()).isEqualTo(BLACK);
        assertThat(draws.get()).isEqualTo(1);

        drawing.create(GameId.generate(), 2, GameOptions.of(Map.of("hostColour", "white")));
        assertThat(draws.get()).isEqualTo(1);
    }

    @Test
    void drawOffersCanBeSwitchedOff() {
        assertThat(create(Map.of("drawOffers", "off")).drawOffersAllowed()).isFalse();
        assertThat(create(Map.of("drawOffers", "on")).drawOffersAllowed()).isTrue();
    }

    @Test
    void theOptionsAreDeclaredWithTheirKeysDefaultsAndLabels() {
        List<OptionDescriptor> options = factory.optionDescriptors();

        assertThat(options).extracting(OptionDescriptor::key).containsExactly("hostColour", "drawOffers");
        OptionDescriptor colour = options.get(0);
        assertThat(colour.values()).containsExactly("white", "black", "random");
        assertThat(colour.defaultValue()).isEqualTo("random");
        assertThat(colour.labelKey()).isEqualTo("options.draughts.hostColour");
        OptionDescriptor offers = options.get(1);
        assertThat(offers.values()).containsExactly("on", "off");
        assertThat(offers.defaultValue()).isEqualTo("on");
        assertThat(offers.labelKey()).isEqualTo("options.draughts.drawOffers");
    }

    @Test
    void theSeatOptionIsTheBotLevelWithMediumByDefault() {
        List<OptionDescriptor> seat = factory.seatOptionDescriptors();

        assertThat(seat).hasSize(1);
        assertThat(seat.get(0).key()).isEqualTo("level");
        assertThat(seat.get(0).values()).containsExactly("random", "greedy", "expert");
        assertThat(seat.get(0).defaultValue()).isEqualTo("greedy");
        assertThat(seat.get(0).labelKey()).isEqualTo("options.draughts.level");
        factory.validateSeatOption("level", "expert");
        assertThatThrownBy(() -> factory.validateSeatOption("level", "perfect"))
                .isInstanceOf(InvalidOptionException.class);
    }

    @Test
    void validationIsStrict() {
        assertThat(factory.validate(GameOptions.none()).values())
                .containsEntry("hostColour", "random").containsEntry("drawOffers", "on");
        assertThatThrownBy(() -> factory.validate(GameOptions.of(Map.of("hostColour", "green"))))
                .isInstanceOf(InvalidOptionException.class);
        assertThatThrownBy(() -> factory.validate(GameOptions.of(Map.of("clock", "blitz"))))
                .isInstanceOf(InvalidOptionException.class);
    }
}
