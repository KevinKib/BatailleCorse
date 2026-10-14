package org.kevinkib.cardgames.kobo.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.bot.KoboBotView;
import org.kevinkib.cardgames.kobo.domain.power.Power;
import org.kevinkib.cardgames.kobo.domain.scoring.RoundResult;
import org.kevinkib.cardgames.kobo.domain.turn.Action;
import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.kevinkib.cardgames.kobo.presentation.dto.KoboDto;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cards.domain.Card;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.ref;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;

/**
 * The bot sees exactly what a human viewer at its seat receives: the domain view is compared with the
 * seat's {@code KoboDto}, field for field, in every phase and for the current seat and another one.
 */
class KoboBotViewParityTest {

    private static CardDto dto(Card card) {
        return CardDto.from(card);
    }

    private static CardDto dto(Optional<Card> card) {
        return card.map(CardDto::from).orElse(null);
    }

    private static void assertSameAsTheHumanView(Kobo game, PlayerId viewer) {
        KoboBotView view = KoboBotView.forSeat(game, viewer);
        KoboDto human = KoboDto.forViewer(game, viewer);
        String context = game.phase() + " seat " + viewer;

        assertThat(context, view.version(), is(human.version()));
        assertThat(context, view.round(), is(human.round()));
        assertThat(context, view.phase().name(), is(human.phase()));
        assertThat(context, view.currentSeat().id(), is(human.currentSeat()));
        assertThat(context, view.actions().stream().map(Action::name).toList(), is(human.availableActions()));
        assertThat(context, dto(view.discardTop()), is(human.discardTop()));
        assertThat(context, view.discardSize(), is(human.discardSize()));
        assertThat(context, view.drawPileSize(), is(human.drawPileSize()));
        assertThat(context, view.pendingPower().name(), is(human.pendingPower()));
        assertThat(context, view.announcer().map(PlayerId::id).orElse(null), is(human.announcer()));
        assertThat(context, view.pendingFinalTurns().stream().map(PlayerId::id).toList(),
                is(human.finalTurnsRemaining()));
        assertThat(context, view.readySeats().stream().map(PlayerId::id).sorted().toList(), is(human.readySeats()));
        assertThat(context, view.revealed(), is(human.revealed()));
        assertThat(context, view.hasDrawn(), is(human.hasDrawn()));
        assertThat(context, dto(view.drawn()), is(human.drawnCard()));
        assertThat(context, view.startingCards().entrySet().stream()
                .map(e -> new KoboDto.SlotCardDto(e.getKey(), dto(e.getValue()))).toList(), is(human.startingCards()));

        if (view.reveal().isPresent()) {
            var reveal = view.reveal().get();
            assertThat(context, new KoboDto.RevealDto(reveal.ref().seat().id(), reveal.ref().slot(),
                    reveal.revision(), dto(reveal.card())), is(human.reveal()));
        } else {
            assertThat(context, human.reveal(), is(nullValue()));
        }

        assertThat(context, view.seats().stream().map(PlayerId::id).toList(),
                is(human.players().stream().map(KoboDto.PlayerDto::seat).toList()));
        for (KoboDto.PlayerDto player : human.players()) {
            PlayerId id = seat(player.seat());
            assertThat(context, view.totals().get(id), is(player.total()));
            assertThat(context, view.handSize(id), is(player.handSize()));
            List<KoboBotView.SlotView> slots = view.slots(id);
            assertThat(context, slots.size(), is(player.slots().size()));
            for (int i = 0; i < slots.size(); i++) {
                KoboDto.SlotDto shown = player.slots().get(i);
                assertThat(context, slots.get(i).slot(), is(shown.slot()));
                assertThat(context, slots.get(i).occupied(), is(shown.occupied()));
                assertThat(context, slots.get(i).revision(), is(shown.revision()));
                assertThat(context, dto(slots.get(i).card()), is(shown.card()));
            }
        }

        if (view.lastRound().isPresent()) {
            RoundResult round = view.lastRound().get();
            KoboDto.RoundDto shown = human.lastRound();
            assertThat(context, shown.announcer(), is(round.announcer() == null ? null : round.announcer().id()));
            assertThat(context, shown.announcerWon(), is(round.announcerWon()));
            assertThat(context, shown.giftTo(), is(round.giftTo() == null ? null : round.giftTo().id()));
            for (KoboDto.PlayerRoundDto line : shown.players()) {
                PlayerId id = seat(line.seat());
                assertThat(context, round.handValues().get(id), is(line.handValue()));
                assertThat(context, round.roundPoints().get(id), is(line.roundPoints()));
                assertThat(context, round.totalsBefore().get(id), is(line.totalBefore()));
                assertThat(context, round.totalsAfter().get(id), is(line.totalAfter()));
                assertThat(context, round.reset().contains(id), is(line.reset()));
            }
        } else {
            assertThat(context, human.lastRound(), is(nullValue()));
        }
    }

    private static void assertParityForEverySeat(Kobo game) {
        for (PlayerId viewer : game.getPlayerIds()) {
            assertSameAsTheHumanView(game, viewer);
        }
    }

    @Test
    void inMemorisingWithTheStartingCards() {
        assertParityForEverySeat(aKobo().withPlayers(3).inPhase(Phase.MEMORISING).build());
    }

    @Test
    void afterOneSeatIsReadyAndThenAfterTheBarrierOpened() throws Exception {
        Kobo game = new Kobo(GameId.generate(), 3, 11L);
        game.ready(seat(1));
        assertParityForEverySeat(game);
        game.ready(seat(0));
        game.ready(seat(2));
        assertParityForEverySeat(game);
    }

    @Test
    void inDrawBeforeAndAfterTheFirstDraw() throws Exception {
        Kobo game = aKobo().withPlayers(3).inPhase(Phase.DRAW).build();
        assertParityForEverySeat(game);
        game.draw(seat(0));
        assertParityForEverySeat(game);
    }

    @Test
    void inDecisionTheDrawnCardIsForTheCurrentSeatOnly() {
        assertParityForEverySeat(aKobo().withPlayers(3).inPhase(Phase.DECISION).currentSeat(1).withDrawn("QS").build());
    }

    @Test
    void inPowerWithEachPendingPower() {
        for (Power power : List.of(Power.PEEK_OWN, Power.PEEK_OTHER, Power.BLIND_SWAP, Power.KING)) {
            assertParityForEverySeat(aKobo().withPlayers(3).inPhase(Phase.POWER).withPendingPower(power).build());
        }
    }

    @Test
    void afterAPeekAndInKingDecision() throws Exception {
        Kobo peeked = aKobo().withPlayers(3).inPhase(Phase.POWER).withPendingPower(Power.PEEK_OTHER).build();
        peeked.peek(seat(0), ref(1, 2));
        assertParityForEverySeat(peeked);

        Kobo king = aKobo().withPlayers(3).inPhase(Phase.POWER).withPendingPower(Power.KING).build();
        king.peek(seat(0), ref(2, 0));
        assertThat(king.phase(), is(Phase.KING_DECISION));
        assertParityForEverySeat(king);
    }

    @Test
    void inTurnEndAndWithAnnouncedFinalTurns() throws Exception {
        Kobo game = aKobo().withPlayers(3).inPhase(Phase.TURN_END).build();
        assertParityForEverySeat(game);
        game.announceKobo(seat(0));
        assertParityForEverySeat(game);
    }

    @Test
    void afterAMatchWithAGiftAndAPenalty() throws Exception {
        Kobo game = aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .withDiscard("2C").inPhase(Phase.DRAW).build();
        game.matchDiscard(seat(1), ref(0, 1), 0, game.discardTop().get().getRank(), 0);
        assertParityForEverySeat(game);
        game.matchDiscard(seat(0), ref(1, 3), null, game.discardTop().get().getRank(), 0);
        assertParityForEverySeat(game);
    }

    @Test
    void inGiftAndRoundOverAndWhenTheGameIsFinished() throws Exception {
        Kobo gift = aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .inPhase(Phase.TURN_END).withDrawPile("2C", "3C").build();
        gift.announceKobo(seat(0));
        gift.draw(seat(1));
        gift.discardDrawn(seat(1));
        gift.endTurn(seat(1));
        assertThat(gift.phase(), is(Phase.GIFT));
        assertParityForEverySeat(gift);

        gift.giveTen(seat(0), seat(1));
        assertThat(gift.phase(), is(Phase.ROUND_OVER));
        assertParityForEverySeat(gift);

        Kobo finished = aKobo().withPlayers(2).build();
        finished.forfeit(seat(0));
        assertParityForEverySeat(finished);
    }

    @Test
    void theBotFlagIsTheOnlyThingTheHumanViewAddsForABotSeat() {
        Kobo game = aKobo().withPlayers(2).inPhase(Phase.DRAW).build();

        KoboDto plain = KoboDto.forViewer(game, seat(0), Map.of());
        KoboDto withBot = KoboDto.forViewer(game, seat(0), Map.of(), Set.of(seat(1)));

        assertThat(withBot.players().get(1).bot(), is(true));
        assertThat(withBot.players().get(0), is(plain.players().get(0)));
        assertThat(withBot.hasDrawn(), is(plain.hasDrawn()));
    }
}
