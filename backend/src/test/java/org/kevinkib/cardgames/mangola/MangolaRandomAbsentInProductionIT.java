package org.kevinkib.cardgames.mangola;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.presentation.bot.ThreadLocalRandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.random.RandomGenerator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

/** Without the {@code test} profile the draw of the first player and of the random set-up is genuinely random. */
@SpringBootTest
class MangolaRandomAbsentInProductionIT {

    @Autowired
    private ApplicationContext context;

    @Test
    void theProductionContextHoldsAThreadLocalGeneratorForMangola() {
        assertThat(context.containsBean("mangolaRandom"), is(true));
        assertThat(context.getBean("mangolaRandom", RandomGenerator.class), instanceOf(ThreadLocalRandomGenerator.class));
    }
}
