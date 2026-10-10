package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.presentation.bot.BullshitBotCoordinator;
import org.kevinkib.cardgames.bullshit.presentation.bot.ThinkingDelay;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

class BotWiringTest {

    @Nested
    @SpringBootTest
    class DefaultProfile {

        @Autowired
        ThinkingDelay thinkingDelay;

        @Autowired
        BullshitBotCoordinator coordinator;

        @Autowired
        List<GameEvictionListener> evictionListeners;

        @Test
        void thinkingDelayIsBetweenOneAndTwoSeconds() {
            for (int i = 0; i < 50; i++) {
                Duration delay = thinkingDelay.next();
                assertThat(delay.toMillis(), greaterThanOrEqualTo(1000L));
                assertThat(delay.toMillis(), lessThanOrEqualTo(2000L));
            }
        }

        @Test
        void coordinatorIsWiredAndReleasedWhenAGameIsEvicted() {
            assertThat(evictionListeners, hasItem(instanceOf(BullshitBotCoordinator.class)));
            assertThat(coordinator != null, is(true));
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    class TestProfile {

        @Autowired
        ThinkingDelay thinkingDelay;

        @Test
        void thinkingDelayIsPinnedToZero() {
            assertThat(thinkingDelay.next(), is(Duration.ZERO));
        }
    }
}
