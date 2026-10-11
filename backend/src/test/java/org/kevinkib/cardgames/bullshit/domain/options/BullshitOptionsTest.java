package org.kevinkib.cardgames.bullshit.domain.options;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.bot.ScriptedRandom;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimModeOption;
import org.kevinkib.cardgames.bullshit.domain.deck.DeckSize;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BullshitOptionsTest {

    @Test
    void givenSuitKey_whenFrom_thenSuitClaimMode() {
        BullshitOptions options = BullshitOptions.from(GameOptions.of(Map.of("claimMode", "suit")));
        assertThat(options.claimMode(), is(ClaimModeOption.SUIT));
    }

    @Test
    void givenNoOptions_whenFrom_thenRankDefault() {
        BullshitOptions options = BullshitOptions.from(GameOptions.none());
        assertThat(options.claimMode(), is(ClaimModeOption.RANK));
    }

    @Test
    void givenUnknownKey_whenFrom_thenRankDefault() {
        BullshitOptions options = BullshitOptions.from(GameOptions.of(Map.of("claimMode", "bogus")));
        assertThat(options.claimMode(), is(ClaimModeOption.RANK));
    }

    @Test
    void givenSuit_whenToClaimMode_thenInitialTargetIsHeart() {
        BullshitOptions options = new BullshitOptions(ClaimModeOption.SUIT);
        assertThat(options.toClaimMode(new ScriptedRandom(0.0)).initial().label(), is("HEART"));
    }

    @Test
    void givenUnknownKey_whenParseStrict_thenRefused() {
        assertThrows(InvalidOptionException.class,
                () -> BullshitOptions.parseStrict(GameOptions.of(Map.of("bogus", "x"))));
    }

    @Test
    void givenUnknownClaimMode_whenParseStrict_thenRefused() {
        assertThrows(InvalidOptionException.class,
                () -> BullshitOptions.parseStrict(GameOptions.of(Map.of("claimMode", "bogus"))));
    }

    @Test
    void givenUnknownDeckSize_whenParseStrict_thenRefused() {
        assertThrows(InvalidOptionException.class,
                () -> BullshitOptions.parseStrict(GameOptions.of(Map.of("deckSize", "40"))));
    }

    @Test
    void givenNoOptions_whenParseStrict_thenDefaults() {
        BullshitOptions options = BullshitOptions.parseStrict(GameOptions.none());
        assertThat(options.claimMode(), is(ClaimModeOption.RANK));
        assertThat(options.deckSize(), is(DeckSize.DEFAULT));
    }

    @Test
    void givenValidValues_whenParseStrict_thenParsed() {
        BullshitOptions options = BullshitOptions.parseStrict(
                GameOptions.of(Map.of("claimMode", "suit", "deckSize", "52")));
        assertThat(options.claimMode(), is(ClaimModeOption.SUIT));
        assertThat(options.deckSize(), is(DeckSize.FULL));
    }

    @Test
    void givenUnknownValuesAndKeys_whenFrom_thenStillLenientWithDefaults() {
        BullshitOptions options = BullshitOptions.from(
                GameOptions.of(Map.of("claimMode", "bogus", "deckSize", "40", "extra", "x")));
        assertThat(options.claimMode(), is(ClaimModeOption.RANK));
        assertThat(options.deckSize(), is(DeckSize.DEFAULT));
    }

    @Test
    void givenOptions_whenToGameOptions_thenFullMapOfKeys() {
        assertThat(new BullshitOptions(ClaimModeOption.SUIT, DeckSize.FULL).toGameOptions().values(),
                is(Map.of("claimMode", "suit", "deckSize", "52")));
    }
}
