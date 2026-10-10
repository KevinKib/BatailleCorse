package org.kevinkib.cardgames.bullshit.domain.options;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.bot.ScriptedRandom;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimModeOption;
import org.kevinkib.cardgames.bullshit.domain.deck.DeckSize;
import org.kevinkib.cardgames.game.GameOptions;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class BullshitOptionsTest {

    @Test
    void givenSuitKey_whenFrom_thenSuitClaimMode() {
        BullshitOptions options = BullshitOptions.from(GameOptions.of(Map.of("claimMode", "suit")), 2);
        assertThat(options.claimMode(), is(ClaimModeOption.SUIT));
    }

    @Test
    void givenNoOptions_whenFrom_thenRankDefault() {
        BullshitOptions options = BullshitOptions.from(GameOptions.none(), 2);
        assertThat(options.claimMode(), is(ClaimModeOption.RANK));
    }

    @Test
    void givenUnknownKey_whenFrom_thenRankDefault() {
        BullshitOptions options = BullshitOptions.from(GameOptions.of(Map.of("claimMode", "bogus")), 2);
        assertThat(options.claimMode(), is(ClaimModeOption.RANK));
    }

    @Test
    void givenSuit_whenToClaimMode_thenInitialTargetIsHeart() {
        BullshitOptions options = new BullshitOptions(ClaimModeOption.SUIT);
        assertThat(options.toClaimMode(new ScriptedRandom(0.0)).initial().label(), is("HEART"));
    }

    @Test
    void givenNoDeckSize_whenFrom_thenDefaultFollowsPlayerCount() {
        assertThat(BullshitOptions.from(GameOptions.none(), 3).deckSize(), is(DeckSize.SHORT));
        assertThat(BullshitOptions.from(GameOptions.none(), 4).deckSize(), is(DeckSize.FULL));
    }

    @Test
    void givenExplicitDeckSize_whenFrom_thenItWinsOverPlayerCount() {
        assertThat(BullshitOptions.from(GameOptions.of(Map.of("deckSize", "52")), 2).deckSize(), is(DeckSize.FULL));
        assertThat(BullshitOptions.from(GameOptions.of(Map.of("deckSize", "32")), 6).deckSize(), is(DeckSize.SHORT));
    }

    @Test
    void givenNoDeckSize_whenEffectiveOptions_thenResolvedDeckSizeIsExposedAndExplicitKept() {
        BullshitFactory factory = new BullshitFactory();
        assertThat(factory.effectiveOptions(GameOptions.none(), 2).get("deckSize").orElse(null), is("32"));
        assertThat(factory.effectiveOptions(GameOptions.none(), 5).get("deckSize").orElse(null), is("52"));
        assertThat(factory.effectiveOptions(GameOptions.of(Map.of("deckSize", "52")), 2).get("deckSize").orElse(null), is("52"));
    }
}
