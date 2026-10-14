package org.kevinkib.cardgames.threemenmorris;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.presentation.bot.ThreadLocalRandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.Set;
import java.util.random.RandomGenerator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

/** Without the {@code test} profile the Morris colour draw is genuinely random. */
@SpringBootTest
class MorrisRandomAbsentInProductionIT {

    @Autowired
    private ApplicationContext context;

    @Test
    void theProductionContextHoldsOnlyThreadLocalGenerators() {
        // the Morris colour draw and the Kobo bots' forgetting; no constant or seeded test generator
        assertThat(context.getBeansOfType(RandomGenerator.class).keySet(), is(Set.of("morrisRandom", "koboBotRandom")));
        assertThat(context.getBean("morrisRandom"), instanceOf(ThreadLocalRandomGenerator.class));
        assertThat(context.getBean("koboBotRandom"), instanceOf(ThreadLocalRandomGenerator.class));
    }
}
