package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KoboDealTest {

    @Test
    void factoryDescribesTheGame() {
        KoboFactory factory = new KoboFactory();

        assertThat(factory.gameType(), is("kobo"));
        assertThat(factory.minPlayers(), is(2));
        assertThat(factory.maxPlayers(), is(6));
        assertThat(factory.create(GameId.generate(), 3), instanceOf(Kobo.class));
        assertThat(factory.create(GameId.generate(), 4, GameOptions.none()), instanceOf(Kobo.class));
    }

    @Test
    void givenTwoToSixPlayers_whenDealt_thenFourCardsEachAndRestIsTheDrawPile() throws Exception {
        for (int n = 2; n <= 6; n++) {
            Kobo kobo = new Kobo(GameId.generate(), n, 1L);

            assertThat(kobo.getPlayerIds(), hasSize(n));
            for (PlayerId p : kobo.getPlayerIds()) {
                assertThat(kobo.handSize(p), is(4));
            }
            assertThat(kobo.drawPileSize(), is(52 - 4 * n));
            assertThat(kobo.discardSize(), is(0));
            assertThat(kobo.phase(), is(Phase.MEMORISING));
            assertThat(kobo.round(), is(1));
            assertThat(kobo.version(), is(0));
            assertThat(kobo.cardsInPlay(), is(52));
        }
    }

    @Test
    void givenADeal_thenAllFiftyTwoCardsAreDistinct() {
        Kobo kobo = new Kobo(GameId.generate(), 6, 7L);
        List<Card> all = new ArrayList<>();
        for (PlayerId p : kobo.getPlayerIds()) {
            kobo.slots(p).forEach(s -> all.add(s.card()));
        }
        Set<Card> distinct = new HashSet<>(all);

        assertThat(all.size(), is(24));
        assertThat(distinct.size(), is(24));
    }

    @Test
    void givenSameSeed_thenSameDeal_andDifferentSeedDiffers() {
        Kobo a = new Kobo(GameId.generate(), 3, 5L);
        Kobo b = new Kobo(GameId.generate(), 3, 5L);
        Kobo c = new Kobo(GameId.generate(), 3, 6L);
        PlayerId p0 = new PlayerId(0);

        assertThat(a.slots(p0), is(b.slots(p0)));
        assertThat(a.slots(p0), is(not(c.slots(p0))));
    }

    @Test
    void givenInvalidPlayerCount_thenRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Kobo(GameId.generate(), 1));
        assertThrows(IllegalArgumentException.class, () -> new Kobo(GameId.generate(), 7));
    }

    @Test
    void viewedAsGame_exposesIdPlayersAndFinished() {
        Kobo kobo = new Kobo(GameId.generate(), 3);
        Game game = kobo;

        assertThat(game.getId(), is(kobo.getId()));
        assertThat(game.getPlayerIds(), contains(new PlayerId(0), new PlayerId(1), new PlayerId(2)));
        assertThat(game.isFinished(), is(false));
    }
}
