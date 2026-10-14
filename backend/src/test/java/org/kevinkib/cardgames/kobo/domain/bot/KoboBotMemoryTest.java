package org.kevinkib.cardgames.kobo.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.power.Power;
import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.ref;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.match;

class KoboBotMemoryTest {

    private static KoboBotMemory memory(int seat, boolean followsMoves) {
        return new KoboBotMemory(seat(seat), followsMoves);
    }

    private static void observe(KoboBotMemory memory, Kobo kobo) {
        memory.observe(KoboBotView.forSeat(kobo, memory.seat()));
    }

    private static Kobo twoPlayerGameAtRoundTwo() throws Exception {
        Kobo kobo = new Kobo(GameId.generate(), 2, 11L);
        kobo.ready(seat(0));
        kobo.ready(seat(1));
        kobo.draw(seat(0));
        kobo.swapDrawn(seat(0), 0);
        kobo.announceKobo(seat(0));
        kobo.draw(seat(1));
        kobo.swapDrawn(seat(1), 0);
        kobo.endTurn(seat(1));
        if (kobo.phase() == Phase.GIFT) {
            kobo.giveTen(seat(0), seat(1));
        }
        kobo.ready(seat(0));
        kobo.ready(seat(1));
        return kobo;
    }

    @Test
    void startingCardsAreKeptAfterTheFirstDraw() throws Exception {
        Kobo kobo = new Kobo(GameId.generate(), 2, 5L);
        KoboBotMemory memory = memory(0, true);
        observe(memory, kobo);
        var start = KoboBotView.forSeat(kobo, seat(0)).startingCards();
        assertThat(memory.known(ref(0, 2)).get().card(), is(start.get(2)));
        assertThat(memory.known(ref(0, 3)).get().card(), is(start.get(3)));
        assertThat(memory.known(ref(0, 0)).isPresent(), is(false));

        kobo.ready(seat(0));
        kobo.ready(seat(1));
        kobo.draw(seat(0));
        observe(memory, kobo);

        assertThat(memory.known(ref(0, 2)).get().card(), is(start.get(2)));
        assertThat(memory.known(ref(0, 3)).get().card(), is(start.get(3)));
    }

    @Test
    void aPeekIsKeptAfterTheNextCommand() throws Exception {
        Kobo kobo = aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .inPhase(Phase.POWER).withPendingPower(Power.PEEK_OTHER).build();
        KoboBotMemory memory = memory(0, true);
        kobo.peek(seat(0), ref(1, 2));
        observe(memory, kobo);
        assertThat(memory.known(ref(1, 2)).get().card(), is(card("9D")));

        kobo.endTurn(seat(0));
        assertThat(KoboBotView.forSeat(kobo, seat(0)).reveal().isPresent(), is(false));
        observe(memory, kobo);

        assertThat(memory.known(ref(1, 2)).get().card(), is(card("9D")));
        assertThat(memory.known(ref(1, 2)).get().revision(), is(0));
    }

    @Test
    void aRevisionChangeForgetsTheSlot() throws Exception {
        Kobo kobo = aKobo().withPlayers(2).inPhase(Phase.DECISION).withDrawn("5H").build();
        KoboBotMemory memory = memory(0, true);
        memory.learn(ref(0, 1), card("2H"), 0);
        memory.learn(ref(0, 2), card("3H"), 0);
        observe(memory, kobo);
        assertThat(memory.known(ref(0, 1)).isPresent(), is(true));

        kobo.swapDrawn(seat(0), 1);
        observe(memory, kobo);

        assertThat(memory.known(ref(0, 1)).isPresent(), is(false));
        assertThat(memory.known(ref(0, 2)).get().card(), is(card("3H")));
    }

    @Test
    void theOwnDrawnCardPlacedBySwapIsRememberedAtItsNewRevision() throws Exception {
        Kobo kobo = aKobo().withPlayers(2).inPhase(Phase.DECISION).withDrawn("5H").build();
        KoboBotMemory memory = memory(0, false);
        observe(memory, kobo);

        kobo.swapDrawn(seat(0), 1);
        memory.onOwnDrawnPlaced(1);
        observe(memory, kobo);

        assertThat(memory.known(ref(0, 1)).get().card(), is(card("5H")));
        assertThat(memory.known(ref(0, 1)).get().revision(), is(1));
    }

    @Test
    void aBlindSwapRelocatesBothKnownCardsWhenMovesAreFollowed() throws Exception {
        Kobo kobo = aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .withTableau("7C", "8C", "QC", "JC")
                .inPhase(Phase.POWER).withPendingPower(Power.BLIND_SWAP).build();
        KoboBotMemory memory = memory(2, true);
        memory.learn(ref(0, 1), card("2H"), 0);
        memory.learn(ref(1, 2), card("9D"), 0);
        observe(memory, kobo);

        kobo.blindSwap(seat(0), 1, ref(1, 2));
        memory.onSwapped(ref(0, 1), ref(1, 2));
        observe(memory, kobo);

        assertThat(memory.known(ref(1, 2)).get().card(), is(card("2H")));
        assertThat(memory.known(ref(1, 2)).get().revision(), is(1));
        assertThat(memory.known(ref(0, 1)).get().card(), is(card("9D")));
        assertThat(memory.known(ref(0, 1)).get().revision(), is(1));
    }

    @Test
    void aBlindSwapOnlyForgetsWhenMovesAreNotFollowed() throws Exception {
        Kobo kobo = aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .withTableau("7C", "8C", "QC", "JC")
                .inPhase(Phase.POWER).withPendingPower(Power.BLIND_SWAP).build();
        KoboBotMemory memory = memory(2, false);
        memory.learn(ref(0, 1), card("2H"), 0);
        memory.learn(ref(1, 2), card("9D"), 0);
        memory.learn(ref(2, 0), card("7C"), 0);
        observe(memory, kobo);

        kobo.blindSwap(seat(0), 1, ref(1, 2));
        memory.onSwapped(ref(0, 1), ref(1, 2));
        observe(memory, kobo);

        assertThat(memory.known(ref(1, 2)).isPresent(), is(false));
        assertThat(memory.known(ref(0, 1)).isPresent(), is(false));
        assertThat(memory.known(ref(2, 0)).get().card(), is(card("7C")));
    }

    @Test
    void aKingSwapRelocatesBothKnownCards() throws Exception {
        Kobo kobo = aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .withTableau("7C", "8C", "QC", "JC")
                .inPhase(Phase.POWER).withPendingPower(Power.KING).build();
        KoboBotMemory memory = memory(2, true);
        memory.learn(ref(0, 3), card("4H"), 0);
        memory.learn(ref(1, 0), card("5S"), 0);
        kobo.peek(seat(0), ref(1, 0));
        kobo.kingSwap(seat(0), 3);

        memory.onSwapped(ref(0, 3), ref(1, 0));
        observe(memory, kobo);

        assertThat(memory.known(ref(1, 0)).get().card(), is(card("4H")));
        assertThat(memory.known(ref(0, 3)).get().card(), is(card("5S")));
    }

    @Test
    void aMatchGiftMovesTheGivenCardAndDropsTheMatchedOne() throws Exception {
        Kobo kobo = aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .withTableau("7C", "8C", "QC", "JC")
                .inPhase(Phase.DRAW).withDiscard("AS").build();
        KoboBotMemory memory = memory(2, true);
        memory.learn(ref(0, 0), card("AH"), 0);
        memory.learn(ref(1, 3), card("KS"), 0);
        observe(memory, kobo);

        match(kobo, seat(1), ref(0, 0), 3, FrenchRank.ACE);
        memory.onMoved(ref(1, 3), ref(0, 0));
        observe(memory, kobo);

        assertThat(memory.known(ref(0, 0)).get().card(), is(card("KS")));
        assertThat(memory.known(ref(1, 3)).isPresent(), is(false));
    }

    @Test
    void aMatchGiftIsForgottenWhenMovesAreNotFollowed() throws Exception {
        Kobo kobo = aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .withTableau("7C", "8C", "QC", "JC")
                .inPhase(Phase.DRAW).withDiscard("AS").build();
        KoboBotMemory memory = memory(2, false);
        memory.learn(ref(0, 0), card("AH"), 0);
        memory.learn(ref(1, 3), card("KS"), 0);

        match(kobo, seat(1), ref(0, 0), 3, FrenchRank.ACE);
        memory.onMoved(ref(1, 3), ref(0, 0));
        observe(memory, kobo);

        assertThat(memory.knownSlots(seat(0)), is(empty()));
        assertThat(memory.knownSlots(seat(1)), is(empty()));
    }

    @Test
    void aNewRoundClearsEverything() throws Exception {
        Kobo kobo = new Kobo(GameId.generate(), 2, 11L);
        KoboBotMemory memory = memory(0, true);
        observe(memory, kobo);
        assertThat(memory.knownSlots(seat(0)).size(), is(2));
        memory.learn(ref(1, 0), card("AH"), 0);
        memory.onOwnTurnEnded(new ScriptedRandom(), 0);

        Kobo next = twoPlayerGameAtRoundTwo();
        assertThat(next.round(), is(2));
        observe(memory, next);

        var start = KoboBotView.forSeat(next, seat(0)).startingCards();
        assertThat(memory.knownSlots(seat(1)), is(empty()));
        assertThat(memory.known(ref(0, 2)).get().card(), is(start.get(2)));
        assertThat(memory.knownSlots(seat(0)).size(), is(2));
        assertThat(memory.ownTurns(), is(0));
    }

    @Test
    void forgettingDropsAnEntryWhenTheDrawIsBelowTheProbability() {
        KoboBotMemory memory = memory(0, true);
        memory.learn(ref(0, 0), card("AH"), 0);
        memory.learn(ref(0, 1), card("2H"), 0);
        ScriptedRandom random = new ScriptedRandom(0.0, 0.99);

        memory.forget(random, 0.08);

        assertThat(memory.known(ref(0, 0)).isPresent(), is(false));
        assertThat(memory.known(ref(0, 1)).get().card(), is(card("2H")));
        assertThat(random.drawn(), is(2));
    }

    @Test
    void forgettingWithAZeroProbabilityDrawsNothing() {
        KoboBotMemory memory = memory(0, true);
        memory.learn(ref(0, 0), card("AH"), 0);

        memory.forget(new ScriptedRandom(), 0);

        assertThat(memory.known(ref(0, 0)).isPresent(), is(true));
    }

    @Test
    void endingAnOwnTurnCountsItAndForgets() {
        KoboBotMemory memory = memory(0, true);
        memory.learn(ref(0, 0), card("AH"), 0);

        memory.onOwnTurnEnded(new ScriptedRandom(0.0), 0.25);

        assertThat(memory.ownTurns(), is(1));
        assertThat(memory.known(ref(0, 0)).isPresent(), is(false));
    }

    @Test
    void queriesListOwnCardsFirst() {
        KoboBotMemory memory = memory(1, true);
        memory.learn(ref(0, 3), card("9H"), 0);
        memory.learn(ref(1, 2), card("9S"), 0);
        memory.learn(ref(1, 0), card("9C"), 0);
        memory.learn(ref(2, 1), card("4C"), 0);

        assertThat(memory.knownOfRank(FrenchRank.NINE).stream().map(KoboBotMemory.Known::ref).toList(),
                contains(ref(1, 0), ref(1, 2), ref(0, 3)));
        assertThat(memory.knownSlots(seat(1)).stream().map(KoboBotMemory.Known::ref).toList(),
                contains(ref(1, 0), ref(1, 2)));
    }
}
