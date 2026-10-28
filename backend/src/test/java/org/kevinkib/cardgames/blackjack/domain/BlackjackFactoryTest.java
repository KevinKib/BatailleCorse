package org.kevinkib.cardgames.blackjack.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
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

class BlackjackFactoryTest {

    private final BlackjackFactory factory = new BlackjackFactory();

    @Test
    void itIsTheBlackjackGameForOneToSixSeatsWithBots() {
        assertEquals("blackjack", factory.gameType());
        assertEquals("blackjack", BlackjackFactory.GAME_TYPE);
        assertEquals(1, factory.minPlayers());
        assertEquals(6, factory.maxPlayers());
        assertTrue(factory.supportsBots());
    }

    @Test
    void itCreatesGamesAtTheBounds() {
        Game solo = factory.create(GameId.generate(), 1);
        Game full = factory.create(GameId.generate(), 6);
        assertInstanceOf(Blackjack.class, solo);
        assertEquals(1, solo.getPlayerIds().size());
        assertEquals(6, full.getPlayerIds().size());
    }

    @Test
    void itRefusesSeatCountsOutsideTheBounds() {
        assertThrows(IllegalArgumentException.class, () -> factory.create(GameId.generate(), 0));
        assertThrows(IllegalArgumentException.class, () -> factory.create(GameId.generate(), 7));
    }

    @Test
    void itAppliesTheOptionsToTheGame() {
        Blackjack game = (Blackjack) factory.create(GameId.generate(), 2,
                GameOptions.of(Map.of("hands", "15", "maxBet", "100")));
        assertEquals(15, game.handsTotal());
        assertEquals(BlackjackOptions.MaxBet.HUNDRED, game.options().maxBet());
        assertEquals(10, ((Blackjack) factory.create(GameId.generate(), 2)).handsTotal());
    }

    @Test
    void itDescribesTheHostOptionsDefaultFirst() {
        List<OptionDescriptor> descriptors = factory.optionDescriptors();
        assertEquals(List.of("hands", "maxBet"), descriptors.stream().map(OptionDescriptor::key).toList());
        OptionDescriptor hands = descriptors.get(0);
        assertEquals(OptionType.CHOICE, hands.type());
        assertEquals(List.of("10", "5", "15", "20"), hands.values());
        assertEquals("10", hands.defaultValue());
        assertEquals("options.blackjack.hands", hands.labelKey());
        OptionDescriptor maxBet = descriptors.get(1);
        assertEquals(List.of("50", "25", "100"), maxBet.values());
        assertEquals("50", maxBet.defaultValue());
        assertEquals("options.blackjack.maxBet", maxBet.labelKey());
    }

    @Test
    void itDescribesTheBotLevelSeatOption() {
        OptionDescriptor level = factory.seatOptionDescriptors().get(0);
        assertEquals("level", level.key());
        assertEquals(List.of("easy", "medium", "hard"), level.values());
        assertEquals("medium", level.defaultValue());
        assertEquals("options.blackjack.level", level.labelKey());
        factory.validateSeatOption("level", "hard");
        assertThrows(InvalidOptionException.class, () -> factory.validateSeatOption("level", "expert"));
    }

    @Test
    void itValidatesOptionsStrictlyAndCompletesThem() {
        GameOptions completed = factory.validate(GameOptions.of(Map.of("hands", "5")));
        assertEquals("5", completed.get("hands").orElseThrow());
        assertEquals("50", completed.get("maxBet").orElseThrow());
        assertThrows(InvalidOptionException.class, () -> factory.validate(GameOptions.of(Map.of("hands", "7"))));
        assertThrows(InvalidOptionException.class, () -> factory.validate(GameOptions.of(Map.of("maxBet", "30"))));
        assertThrows(InvalidOptionException.class, () -> factory.validate(GameOptions.of(Map.of("decks", "6"))));
    }
}
