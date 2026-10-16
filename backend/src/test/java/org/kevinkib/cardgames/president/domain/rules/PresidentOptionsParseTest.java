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
        assertThat(parsed.toGameOptions().values(), is(Map.of(
                "afterPass", "reenter", "firstLead", "kingOfHearts", "exchange", "two", "giveBack", "free",
                "nextLead", "scum", "scoring", "rank", "length", "rounds-5", "equalPlay", "on")));
    }

    /** One row per value of every option the P3 server declares. */
    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
            "afterPass, reenter", "afterPass, out",
            "firstLead, kingOfHearts", "firstLead, lowestClub", "firstLead, queenOfHearts", "firstLead, random",
            "exchange, two", "exchange, one", "exchange, scaled", "exchange, none",
            "giveBack, free", "giveBack, lowest",
            "nextLead, scum", "nextLead, president",
            "scoring, rank", "scoring, titles", "scoring, zeroSum",
            "length, rounds-5", "length, rounds-3", "length, rounds-10", "length, points-11", "length, points-21",
            "length, endless", "equalPlay, on", "equalPlay, off",
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

    /** The pruned options (jokers, decks, eight stops, putsch, fall) and the variants not shipped yet are unknown keys. */
    @ParameterizedTest
    @ValueSource(strings = {"jokers", "decks", "eightClears", "putsch", "presidentFall", "twoCuts",
            "quadClears", "forbiddenFinish", "mustPlay", "revolution", "ranking", "runs", "suitLock", "bogus", ""})
    void anUnknownKeyIsRefused(String key) {
        assertThrows(InvalidOptionException.class, () -> PresidentOptions.parseStrict(with(key, "on")));
    }

    @ParameterizedTest
    @CsvSource({
            "afterPass, maybe", "afterPass, REENTER", "firstLead, 3C", "firstLead, KING_OF_HEARTS",
            "exchange, three", "giveBack, high", "nextLead, nobody", "scoring, points", "length, rounds-4",
            "length, ENDLESS", "length, ''",
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
