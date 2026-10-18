package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.domain.rules.AfterPass;
import org.kevinkib.cardgames.president.domain.rules.MatchLength;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

class PresidentFactoryTest {

    private final PresidentFactory factory = new PresidentFactory();

    @Test
    void theFactoryDeclaresThreeToEightPlayersAndComputerSeatsWithALevel() {
        assertThat(factory.gameType(), is("president"));
        assertThat(PresidentFactory.GAME_TYPE, is("president"));
        assertThat(factory.minPlayers(), is(3));
        assertThat(factory.maxPlayers(), is(8));
        assertThat(factory.supportsBots(), is(true));
        assertThat(factory.seatOptionDescriptors(), is(List.of(new org.kevinkib.cardgames.game.OptionDescriptor(
                "level", org.kevinkib.cardgames.game.OptionType.CHOICE, List.of("easy", "medium", "hard"), "medium",
                "options.president.level"))));
    }

    @Test
    void aSeatLevelIsValidatedStrictly() {
        factory.validateSeatOption("level", "hard");
        org.junit.jupiter.api.Assertions.assertThrows(org.kevinkib.cardgames.game.InvalidOptionException.class,
                () -> factory.validateSeatOption("level", "expert"));
        org.junit.jupiter.api.Assertions.assertThrows(org.kevinkib.cardgames.game.InvalidOptionException.class,
                () -> factory.validateSeatOption("speed", "hard"));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5, 6, 7, 8})
    void itCreatesAPresidentGameForEachPlayerCount(int players) {
        assertThat(factory.create(GameId.generate(), players), instanceOf(President.class));
        assertThat(factory.create(GameId.generate(), players).getPlayerIds(), hasSize(players));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 9, 10})
    void itRefusesAPlayerCountOutsideTheBounds(int players) {
        assertThrows(IllegalArgumentException.class, () -> factory.create(GameId.generate(), players));
    }

    @Test
    void theOptionDescriptorsCoverTheRoundAndMatchKeysDefaultFirstWithTheirLabelKeys() {
        List<OptionDescriptor> descriptors = factory.optionDescriptors();

        assertThat(descriptors.stream().map(OptionDescriptor::key).toList(), is(List.of(
                "afterPass", "exchange", "giveBack", "nextLead", "scoring", "length", "mustPlay", "reducedCut")));
        assertThat(descriptors.stream().allMatch(d -> d.type() == OptionType.CHOICE), is(true));
        for (OptionDescriptor descriptor : descriptors) {
            assertThat(descriptor.labelKey(), is("options.president." + descriptor.key()));
            assertThat(descriptor.values().get(0), is(descriptor.defaultValue()));
        }
        Map<String, OptionDescriptor> byKey = new java.util.HashMap<>();
        descriptors.forEach(d -> byKey.put(d.key(), d));
        assertThat(byKey.get("afterPass").values(), is(List.of("reenter", "out")));
        assertThat(byKey.get("exchange").values(), is(List.of("two", "one", "scaled", "none")));
        assertThat(byKey.get("giveBack").values(), is(List.of("free", "lowest")));
        assertThat(byKey.get("nextLead").values(), is(List.of("scum", "president")));
        assertThat(byKey.get("scoring").values(), is(List.of("rank", "titles", "zeroSum")));
        assertThat(byKey.get("mustPlay").values(), is(List.of("off", "on")));
        assertThat(byKey.get("reducedCut").values(), is(List.of("off", "on")));
        assertThat(byKey.get("length").values(),
                is(List.of("rounds-5", "rounds-3", "rounds-10", "points-11", "points-21", "endless")));
    }

    @Test
    void validateFillsTheDefaultsOfTheKeysTheHostDidNotSet() {
        GameOptions validated = factory.validate(GameOptions.of(Map.of("afterPass", "out")));

        Map<String, String> expected = new java.util.HashMap<>(Map.ofEntries(
                Map.entry("afterPass", "reenter"), Map.entry("exchange", "two"),
                Map.entry("giveBack", "free"), Map.entry("nextLead", "scum"), Map.entry("scoring", "rank"),
                Map.entry("length", "rounds-5"), Map.entry("mustPlay", "off"), Map.entry("reducedCut", "off")));
        expected.put("afterPass", "out");
        assertThat(validated.values(), is(expected));
    }

    @Test
    void validateAcceptsMustPlayAndStillRefusesTheOthers() {
        GameOptions validated = factory.validate(GameOptions.of(Map.of(
                "mustPlay", "on")));

        assertThat(validated.values().get("mustPlay"), is("on"));
        for (String key : List.of("revolution", "ranking", "runs", "suitLock")) {
            assertThrows(InvalidOptionException.class,
                    () -> factory.validate(GameOptions.of(Map.of(key, "on"))));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"jokers", "decks", "eightClears", "putsch", "presidentFall", "bogus",
            "equalPlay", "firstLead", "forbiddenFinish"})
    void validateRefusesAnUnknownKeyIncludingThePrunedOnes(String key) {
        assertThrows(InvalidOptionException.class,
                () -> factory.validate(GameOptions.of(Map.of(key, "on"))));
    }

    @Test
    void validateRefusesAnUnknownValue() {
        assertThrows(InvalidOptionException.class,
                () -> factory.validate(GameOptions.of(Map.of("length", "rounds-4"))));
    }

    @Test
    void createReadsTheOptionsOfTheRoom() {
        President game = (President) factory.create(GameId.generate(), 4,
                GameOptions.of(Map.of("afterPass", "out", "length", "endless")));

        assertThat(game.options().afterPass(), is(AfterPass.OUT));
        assertThat(game.options().length(), is(MatchLength.ENDLESS));
    }

    @Test
    void theFixedRulesHoldWhateverTheRoomAsked() throws Exception {
        // equal play, the king of hearts lead and the forbidden finish cannot be turned off, even by a stale request
        President game = (President) factory.create(GameId.generate(), 4, GameOptions.of(
                Map.of("equalPlay", "off", "firstLead", "random", "forbiddenFinish", "off")));

        PlayerId leader = game.currentSeat();
        assertThat(game.hand(leader).contains(org.kevinkib.cardgames.president.domain.PresidentFixtures.card("KH")),
                is(true));
        assertThat(game.options(), is(PresidentOptions.defaults()));
    }

    @Test
    void createWithoutOptionsPlaysTheOwnersDefaults() {
        President game = (President) factory.create(GameId.generate(), 4);

        assertThat(game.options().mustPlay(), is(false));
        assertThat(game.options().afterPass(), is(AfterPass.REENTER));
    }

    @Test
    void theFactoryDealsFromItsDeckSource() {
        ScriptedDeckSource source = new ScriptedDeckSource(new RandomDeckSource());
        GameId id = GameId.generate();
        source.fix(id, 1, cards("3C 4C 5C 6C 7C 8C"));

        President game = (President) new PresidentFactory(source).create(id, 3);

        assertThat(game.hand(new PlayerId(0)).containsAll(cards("3C 6C")), is(true));
        assertThat(game.hand(new PlayerId(1)).containsAll(cards("4C 7C")), is(true));
    }
}
