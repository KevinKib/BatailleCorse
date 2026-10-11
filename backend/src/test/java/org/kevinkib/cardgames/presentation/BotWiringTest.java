package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.presentation.bot.BatailleCorseBotCoordinator;
import org.kevinkib.cardgames.bullshit.presentation.bot.BullshitBotCoordinator;
import org.kevinkib.cardgames.bullshit.presentation.bot.CallDecisionDelay;
import org.kevinkib.cardgames.presentation.bot.ThinkingDelay;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameEvictionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
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

        @Autowired
        CallDecisionDelay callDecisionDelay;

        @Test
        void thinkingDelayIsBetweenOneAndAHalfAndThreeAndAHalfSeconds() {
            for (int i = 0; i < 200; i++) {
                Duration delay = thinkingDelay.next();
                assertThat(delay.toMillis(), greaterThanOrEqualTo(1500L));
                assertThat(delay.toMillis(), lessThanOrEqualTo(3500L));
            }
        }

        @Test
        void callDecisionDelayIsBetweenOneAndAHalfAndThreeSeconds() {
            for (int i = 0; i < 200; i++) {
                Duration delay = callDecisionDelay.next();
                assertThat(delay.toMillis(), greaterThanOrEqualTo(1500L));
                assertThat(delay.toMillis(), lessThanOrEqualTo(3000L));
            }
        }

        @Test
        void coordinatorIsWiredAndReleasedWhenAGameIsEvicted() {
            assertThat(evictionListeners, hasItem(instanceOf(BullshitBotCoordinator.class)));
            assertThat(coordinator != null, is(true));
        }

        @Test
        void batailleCorseBotIsWiredAndReleasedWhenAGameIsEvicted() {
            assertThat(evictionListeners, hasItem(instanceOf(BatailleCorseBotCoordinator.class)));
        }

        @Autowired
        Environment environment;

        @Test
        void batailleCorseBotsAreEnabledWithNoExtraDelayByDefault() {
            assertThat(environment.getProperty("bataillecorse.bots.enabled"), is("true"));
            assertThat(environment.getProperty("bataillecorse.bots.extra-delay-ms"), is("0"));
        }
    }

    @Nested
    @SpringBootTest(properties = "bataillecorse.bots.enabled=false")
    class BatailleCorseBotsDisabled {

        @Autowired
        ApplicationContext context;

        @Test
        void noBatailleCorseBotCoordinatorExists() {
            assertThat(context.getBeansOfType(BatailleCorseBotCoordinator.class).isEmpty(), is(true));
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    class TestProfile {

        @Autowired
        ThinkingDelay thinkingDelay;

        @Autowired
        CallDecisionDelay callDecisionDelay;

        @Test
        void thinkingDelayIsPinnedToZero() {
            assertThat(thinkingDelay.next(), is(Duration.ZERO));
        }

        @Test
        void callDecisionDelayIsPinnedToZero() {
            assertThat(callDecisionDelay.next(), is(Duration.ZERO));
        }

        @Autowired
        Environment environment;

        @Test
        void batailleCorseExtraDelayIsZeroInTheTestProfile() {
            assertThat(environment.getProperty("bataillecorse.bots.extra-delay-ms"), is("0"));
        }
    }
}
