package org.kevinkib.cardgames.president.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.bot.PresidentBotView;
import org.kevinkib.cardgames.president.domain.exchange.PendingReturn;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.presentation.dto.PresidentDto;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cards.domain.Card;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/**
 * The bot sees exactly what a human viewer at its seat receives: the domain view is compared with the seat's
 * {@code PresidentDto}, field for field, in every phase and for the seat on turn and the other seats.
 */
class PresidentBotViewParityTest {

    private static List<CardDto> dtos(List<Card> cards) {
        return cards.stream().map(CardDto::from).toList();
    }

    private static void assertSameAsTheHumanView(President game, PlayerId viewer, Set<PlayerId> bots) {
        PresidentBotView view = PresidentBotView.forSeat(game, viewer);
        Map<PlayerId, String> names = new HashMap<>();
        game.getPlayerIds().forEach(p -> names.put(p, "Seat " + p.id()));
        PresidentDto human = PresidentDto.forViewer(game, viewer, names, bots);
        String context = game.phase() + " round " + game.round() + " seat " + viewer.id();

        assertThat(context, view.version(), is(human.version()));
        assertThat(context, view.round(), is(human.round()));
        assertThat(context, view.phase().name(), is(human.phase()));
        assertThat(context, view.currentSeat().id(), is(human.currentSeat()));
        assertThat(context, view.actions().stream().map(Action::name).toList(), is(human.availableActions()));
        assertThat(context, dtos(view.hand()), is(human.hand()));
        assertThat(context, dtos(view.received()), is(human.received()));
        assertThat(context, dtos(view.given()), is(human.given()));
        assertThat(context, view.options().toGameOptions().values(), is(human.options()));
        assertThat(context, dtos(view.completion()), is(human.completion() == null ? List.of() : human.completion()));
        assertThat(context, view.trick().constraint().name(), is(human.trick().constraint()));

        if (viewer.equals(game.currentSeat())) {
            List<PresidentDto.PatternDto> patterns = view.legalPlays().stream()
                    .map(play -> new PresidentDto.PatternDto("SET", play.rank().name(), play.count())).toList();
            assertThat(context, patterns, is(human.legalPlays()));
        } else {
            assertThat(context, human.legalPlays().isEmpty(), is(true));
        }

        assertThat(context, view.trick().leader().id(), is(human.trick().leader()));
        assertThat(context, view.trick().plays().stream()
                .map(p -> new PresidentDto.TrickPlayDto(p.seat().id(), dtos(p.play().cards()))).toList(),
                is(human.trick().plays()));
        assertThat(context, view.trick().passed().stream().map(PlayerId::id).toList(), is(human.trick().passed()));
        Play top = view.trick().top();
        if (top == null) {
            assertThat(context, human.trick().top(), is(nullValue()));
        } else {
            assertThat(context, human.trick().top(),
                    is(new PresidentDto.PatternDto("SET", top.rank().name(), top.count())));
        }

        assertThat(context, view.handSizes().keySet().stream().map(PlayerId::id).toList(),
                is(human.players().stream().map(PresidentDto.PlayerDto::seat).toList()));
        for (PresidentDto.PlayerDto player : human.players()) {
            PlayerId seat = new PlayerId(player.seat());
            assertThat(context, view.handSizes().get(seat), is(player.handSize()));
            assertThat(context, view.totals().getOrDefault(seat, 0), is(player.points()));
            assertThat(context, view.readySeats().contains(seat), is(player.ready()));
            int place = view.finishingOrder().indexOf(seat);
            assertThat(context, place < 0 ? null : place + 1, is(player.place()));
            assertThat(context, view.titles().get(seat) == null ? null : view.titles().get(seat).name(),
                    is(player.title()));
        }

        Set<List<Integer>> pending = new HashSet<>();
        for (PendingReturn p : view.pendingReturns()) {
            pending.add(List.of(p.high().id(), p.low().id(), p.count()));
        }
        Set<List<Integer>> shown = new HashSet<>();
        for (PresidentDto.PairDto p : human.exchange()) {
            if (!p.done()) {
                shown.add(List.of(p.high(), p.low(), p.count()));
            }
        }
        assertThat(context, pending, is(shown));
        int owed = view.pendingReturns().stream().filter(p -> p.high().equals(viewer))
                .mapToInt(PendingReturn::count).sum();
        assertThat(context, owed, is(human.toGiveBack()));
    }

    private static void assertEverySeat(President game, Set<PlayerId> bots) {
        for (PlayerId seat : game.getPlayerIds()) {
            assertSameAsTheHumanView(game, seat, bots);
        }
    }

    @Test
    void theViewMatchesTheDtoInEveryPhaseOfAScriptedMatch() throws Exception {
        Set<PlayerId> bots = Set.of(new PlayerId(1));
        Set<Phase> seen = new HashSet<>();
        for (int seats = 3; seats <= 5; seats++) {
            President game = new President(GameId.generate(), seats, 40L + seats);
            int steps = 0;
            while (!game.isFinished() && steps++ < 2_000) {
                seen.add(game.phase());
                assertEverySeat(game, bots);
                switch (game.phase()) {
                    case PLAYING -> {
                        PlayerId seat = game.currentSeat();
                        List<Play> options = game.legalPlays(seat);
                        if (game.trick().plays().isEmpty() || !options.isEmpty()) {
                            game.play(seat, options.get(0).cards());
                        } else {
                            game.pass(seat);
                        }
                    }
                    case EXCHANGE -> {
                        PendingReturn pending = game.pendingReturns().get(0);
                        List<Card> hand = game.hand(pending.high());
                        game.giveBack(pending.high(), hand.subList(0, pending.count()));
                    }
                    case ROUND_OVER -> game.ready(game.getPlayerIds().stream()
                            .filter(s -> !game.readySeats().contains(s)).findFirst().orElseThrow());
                    default -> throw new AssertionError("unexpected phase " + game.phase());
                }
            }
            assertThat(game.isFinished(), is(true));
        }
        assertThat(seen, hasItem(Phase.PLAYING));
        assertThat(seen, hasItem(Phase.EXCHANGE));
        assertThat(seen, hasItem(Phase.ROUND_OVER));
    }

    @Test
    void theViewMatchesTheDtoWhileACompletionIsOnOffer() throws Exception {
        President game = org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident().leader(0)
                .withHand("9S 3C").withHand("9H 4C").withHand("9D 9C 5C").withHand("10H 6C").build();
        game.play(new PlayerId(0), org.kevinkib.cardgames.president.domain.PresidentFixtures.cards("9S"));
        game.play(new PlayerId(1), org.kevinkib.cardgames.president.domain.PresidentFixtures.cards("9H"));
        assertEverySeat(game, Set.of());
        game.pass(new PlayerId(2));
        assertEverySeat(game, Set.of());

        PresidentBotView holder = PresidentBotView.forSeat(game, new PlayerId(2));

        assertThat(holder.completion().size(), is(2));
        assertThat(holder.actions(), is(List.of(Action.COMPLETE)));
        assertThat(PresidentBotView.forSeat(game, new PlayerId(3)).completion().isEmpty(), is(true));
    }

    @Test
    void theFinishedMatchGivesTheSameViewAndNoAction() throws Exception {
        President game = new President(GameId.generate(), 3, 7L);
        while (!game.isFinished()) {
            switch (game.phase()) {
                case PLAYING -> PresidentRounds.playRoundOut(game);
                case EXCHANGE -> {
                    PendingReturn pending = game.pendingReturns().get(0);
                    game.giveBack(pending.high(), game.hand(pending.high()).subList(0, pending.count()));
                }
                case ROUND_OVER -> PresidentRounds.readyAll(game);
                default -> throw new AssertionError(game.phase());
            }
        }

        assertEverySeat(game, Set.of());
        assertThat(PresidentBotView.forSeat(game, new PlayerId(0)).finished(), is(true));
        assertThat(PresidentBotView.forSeat(game, new PlayerId(0)).actions().isEmpty(), is(true));
    }
}
