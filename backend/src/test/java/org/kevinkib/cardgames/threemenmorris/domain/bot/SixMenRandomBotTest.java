package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class SixMenRandomBotTest {

    private static final PlayerId WHITE = new PlayerId(0);

    @Test
    void afterAMillItOnlyPicksALegalRemoval() {
        MorrisPosition position = SixPositions.removing("WWW.....BBB.B.B.", 0, 0, 0);
        MorrisBot bot = new RandomBot(new Random(7));
        Set<MorrisDecision> seen = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            MorrisDecision decision = bot.decide(position, WHITE, false);
            assertThat(decision, instanceOf(MorrisDecision.Remove.class));
            seen.add(decision);
        }

        // 12 and 14 are outside the mill 8-9-10; the three pieces in it are protected.
        assertThat(seen, containsInAnyOrder(new MorrisDecision.Remove(12), new MorrisDecision.Remove(14)));
        assertThat(seen.size(), is(2));
    }
}
