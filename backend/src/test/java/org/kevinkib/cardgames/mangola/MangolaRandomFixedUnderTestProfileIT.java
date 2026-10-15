package org.kevinkib.cardgames.mangola;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaFactory;
import org.kevinkib.cardgames.mangola.domain.Phase;
import org.kevinkib.cardgames.presentation.bot.ThreadLocalRandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.random.RandomGenerator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/** Under the {@code test} profile every draw is 0: the host starts and the random set-up is deterministic. */
@SpringBootTest
@ActiveProfiles("test")
class MangolaRandomFixedUnderTestProfileIT {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MangolaFactory factory;

    @Test
    void theGeneratorIsConstant() {
        RandomGenerator random = context.getBean("mangolaRandom", RandomGenerator.class);
        assertThat(random, not(instanceOf(ThreadLocalRandomGenerator.class)));
        assertThat(random.nextInt(2), is(0));
    }

    @Test
    void aRandomFirstPlayerIsTheHostAndTheRandomSetupIsTheSameEveryTime() {
        Mangola first = (Mangola) factory.create(GameId.generate(), 2,
                GameOptions.of(Map.of("setup", "random", "firstPlayer", "random")));
        Mangola second = (Mangola) factory.create(GameId.generate(), 2,
                GameOptions.of(Map.of("setup", "random", "firstPlayer", "random")));

        assertThat(first.seatA().id(), is(0));
        assertThat(first.phase(), is(Phase.PLAY));
        assertThat(first.board(), is(second.board()));
    }
}
