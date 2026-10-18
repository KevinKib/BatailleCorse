package org.kevinkib.cardgames.president.domain.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The strict parse of the host's options (the lobby edit) and the lenient parse of room creation. */
class PresidentOptionsParseTest {

    private static GameOptions with(String key, String value) {
        return GameOptions.of(Map.of(key, value));
    }

    @Test
    void noOptionsGiveTheDefaultsAndTheFullMapCarriesEveryKey() {
        PresidentOptions parsed = PresidentOptions.parseStrict(GameOptions.none());

        assertThat(parsed, is(PresidentOptions.defaults()));
        assertThat(parsed.toGameOptions().values(), is(Map.ofEntries(
                Map.entry("afterPass", "reenter"), Map.entry("exchange", "two"),
                Map.entry("giveBack", "free"), Map.entry("nextLead", "scum"), Map.entry("scoring", "rank"),
                Map.entry("length", "rounds-5"), Map.entry("mustPlay", "off"), Map.entry("reducedCut", "off"))));
    }

    /** One row per value of every option the server declares. */
    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
            "afterPass, reenter", "afterPass, out",
            "exchange, two", "exchange, one", "exchange, scaled", "exchange, none",
            "giveBack, free", "giveBack, lowest",
            "nextLead, scum", "nextLead, president",
            "scoring, rank", "scoring, titles", "scoring, zeroSum",
            "length, rounds-5", "length, rounds-3", "length, rounds-10", "length, points-11", "length, points-21",
            "length, endless",
            "mustPlay, off", "mustPlay, on", "reducedCut, off", "reducedCut, on",
    })
    void everyValueIsAcceptedAndRoundTrips(String key, String value) {
        PresidentOptions parsed = PresidentOptions.parseStrict(with(key, value));

        assertThat(parsed.toGameOptions().values().get(key), is(value));
        assertThat(PresidentOptions.parseStrict(parsed.toGameOptions()), is(parsed));
    }

    @Test
    void severalKeysAreParsedTogetherAndTheOthersKeepTheirDefault() {
        PresidentOptions parsed = PresidentOptions.parseStrict(GameOptions.of(
                Map.of("afterPass", "out", "length", "endless", "scoring", "zeroSum")));

        assertThat(parsed.afterPass(), is(AfterPass.OUT));
        assertThat(parsed.length(), is(MatchLength.ENDLESS));
        assertThat(parsed.scoring(), is(Scoring.ZERO_SUM));
        assertThat(parsed.exchange(), is(ExchangeAmount.TWO));
    }

    @Test
    void theMustPlayKeyIsParsedIntoTheRecord() {
        PresidentOptions parsed = PresidentOptions.parseStrict(with("mustPlay", "on"));

        assertThat(parsed.mustPlay(), is(true));
    }

    @Test
    void theReducedCutKeyIsParsedIntoTheRecordAndTheLenientParseToo() {
        assertThat(PresidentOptions.parseStrict(with("reducedCut", "on")).reducedCut(), is(true));
        assertThat(PresidentOptions.from(with("reducedCut", "on")).reducedCut(), is(true));
        assertThat(PresidentOptions.from(with("reducedCut", "maybe")).reducedCut(), is(false));
        assertThat(PresidentOptions.defaults().withReducedCut(true).toGameOptions().values().get("reducedCut"),
                is("on"));
    }

    /** Equal play, the king of hearts lead and the forbidden finish are fixed rules: their former keys are unknown. */
    @ParameterizedTest
    @CsvSource({"equalPlay, on", "equalPlay, off", "firstLead, kingOfHearts", "firstLead, random",
            "forbiddenFinish, lastPlace", "forbiddenFinish, off"})
    void theKeysOfTheFixedRulesAreRefusedLikeAnyUnknownKey(String key, String value) {
        InvalidOptionException refused = assertThrows(InvalidOptionException.class,
                () -> PresidentOptions.parseStrict(with(key, value)));

        assertThat(refused.getMessage(), is("Unknown option " + key));
    }

    @ParameterizedTest
    @ValueSource(strings = {"equalPlay", "firstLead", "forbiddenFinish"})
    void theLenientParseIgnoresTheKeysOfTheFixedRules(String key) {
        assertThat(PresidentOptions.from(with(key, "off")), is(PresidentOptions.defaults()));
        assertThat(PresidentOptions.defaults().toGameOptions().values().containsKey(key), is(false));
    }

    /** The pruned options (jokers, decks, eight stops, putsch, fall) and the variants not shipped yet are unknown keys. */
    @ParameterizedTest
    @ValueSource(strings = {"jokers", "decks", "eightClears", "putsch", "presidentFall",
            "revolution", "ranking", "runs", "suitLock", "bogus", ""})
    void anUnknownKeyIsRefused(String key) {
        assertThrows(InvalidOptionException.class, () -> PresidentOptions.parseStrict(with(key, "on")));
    }

    @ParameterizedTest
    @CsvSource({
            "afterPass, maybe", "afterPass, REENTER",
            "exchange, three", "giveBack, high", "nextLead, nobody", "scoring, points", "length, rounds-4",
            "length, ENDLESS", "length, ''",
            "twoCuts, single", "quadClears, on",
            "mustPlay, yes", "mustPlay, ON", "mustPlay, ''", "reducedCut, yes", "reducedCut, ON",
    })
    void anUnknownValueIsRefusedNotReplacedByTheDefault(String key, String value) {
        assertThrows(InvalidOptionException.class, () -> PresidentOptions.parseStrict(with(key, value)));
    }

    @Test
    void theLenientParseOfRoomCreationFallsBackToTheDefaultOfAnUnknownValue() {
        PresidentOptions parsed = PresidentOptions.from(GameOptions.of(
                Map.of("afterPass", "out", "length", "forever", "jokers", "wild")));

        assertThat(parsed.afterPass(), is(AfterPass.OUT));
        assertThat(parsed.length(), is(MatchLength.ROUNDS_5));
    }
}
