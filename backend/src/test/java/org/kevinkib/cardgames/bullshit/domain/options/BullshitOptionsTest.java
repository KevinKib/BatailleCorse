package org.kevinkib.cardgames.bullshit.domain.options;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimModeOption;
import org.kevinkib.cardgames.game.GameOptions;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
        assertThat(options.toClaimMode().initial().label(), is("HEART"));
    }
}
