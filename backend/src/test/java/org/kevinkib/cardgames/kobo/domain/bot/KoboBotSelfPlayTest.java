package org.kevinkib.cardgames.kobo.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.kobo.domain.Kobo;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;

class KoboBotSelfPlayTest {

    private static final int MAX_COMMANDS = 20_000;
    private static final KoboBotLevel[] MIX = {KoboBotLevel.EASY, KoboBotLevel.MEDIUM, KoboBotLevel.HARD};

    private static List<KoboBotLevel> levels(int seats, int offset) {
        return java.util.stream.IntStream.range(0, seats).mapToObj(i -> MIX[(i + offset) % MIX.length]).toList();
    }

    private void playGames(int seats) throws Exception {
        for (int game = 0; game < 6; game++) {
            long seed = 1000L * seats + game;
            Kobo kobo = new Kobo(GameId.generate(), seats, seed);
            KoboBotHarness harness = new KoboBotHarness(kobo, levels(seats, game), seed);

            boolean finished = harness.run(MAX_COMMANDS);

            assertThat("game " + game + " with " + seats + " bots finished", finished, is(true));
            assertThat(harness.commands, lessThan(MAX_COMMANDS));
            assertThat("no bot is ever penalised", harness.penalties, is(0));
            assertThat(kobo.cardsInPlay(), is(52));
        }
    }

    @Test
    void twoBotsFinishTheGame() throws Exception {
        playGames(2);
    }

    @Test
    void threeBotsFinishTheGame() throws Exception {
        playGames(3);
    }

    @Test
    void sixBotsFinishTheGame() throws Exception {
        playGames(6);
    }

    @Test
    void botsDoMatchCardsTheyRemember() throws Exception {
        int matches = 0;
        for (int game = 0; game < 6; game++) {
            Kobo kobo = new Kobo(GameId.generate(), 3, 77L + game);
            KoboBotHarness harness = new KoboBotHarness(kobo, levels(3, game), 77L + game);
            harness.run(MAX_COMMANDS);
            matches += harness.matches;
        }
        assertThat("some matches happened", matches > 0, is(true));
    }
}
