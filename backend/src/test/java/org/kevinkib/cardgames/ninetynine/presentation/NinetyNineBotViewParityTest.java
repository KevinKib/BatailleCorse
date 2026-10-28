package org.kevinkib.cardgames.ninetynine.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineBotView;
import org.kevinkib.cardgames.ninetynine.presentation.dto.NinetyNineDto;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cards.domain.Card;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.ninetynine.presentation.NinetyNineTable.card;

/**
 * The bot sees exactly what a human viewer at its seat receives: the domain view is compared with the seat's
 * {@code NinetyNineDto}, field for field, in every phase and for the seat on turn and the others.
 */
class NinetyNineBotViewParityTest {

    private final NinetyNineTable table = new NinetyNineTable();

    private static List<CardDto> dtos(List<Card> cards) {
        return cards.stream().map(CardDto::from).toList();
    }

    private static void assertSameAsTheHumanView(NinetyNine game) {
        Map<PlayerId, String> names = new HashMap<>();
        game.getPlayerIds().forEach(p -> names.put(p, "Seat " + p.id()));
        for (PlayerId viewer : game.getPlayerIds()) {
            NinetyNineBotView view = NinetyNineBotView.forSeat(game, viewer);
            NinetyNineDto human = NinetyNineDto.forViewer(game, viewer, names, Set.of(), BotSeatOptions.none());
            String context = game.phase() + " round " + game.round() + " seat " + viewer.id();

            assertThat(view.seat()).as(context).isEqualTo(viewer);
            assertThat(view.version()).as(context).isEqualTo(human.version());
            assertThat(view.round()).as(context).isEqualTo(human.round());
            assertThat(view.phase().name()).as(context).isEqualTo(human.phase());
            assertThat(view.currentSeat().id()).as(context).isEqualTo(human.currentSeat());
            assertThat(view.total()).as(context).isEqualTo(human.total());
            assertThat(view.direction().name()).as(context).isEqualTo(human.direction());
            assertThat(dtos(view.hand())).as(context).isEqualTo(human.hand());
            assertThat(view.stockSize()).as(context).isEqualTo(human.stockSize());
            assertThat(view.options().toGameOptions().values()).as(context).isEqualTo(human.options());
            assertThat(view.pileSinceReshuffle()).as(context).hasSize(human.pileCount());
            assertThat(view.pileSinceReshuffle().isEmpty() ? null : CardDto.from(
                    view.pileSinceReshuffle().get(view.pileSinceReshuffle().size() - 1)))
                    .as(context).isEqualTo(human.pileTop());
            for (NinetyNineDto.PlayerDto player : human.players()) {
                PlayerId seat = new PlayerId(player.seat());
                assertThat(view.handSizes().get(seat)).as(context).isEqualTo(player.handSize());
                assertThat(view.lives().get(seat)).as(context).isEqualTo(player.lives());
                assertThat(view.liveSeats().contains(seat)).as(context).isEqualTo(!player.eliminated());
            }
        }
    }

    @Test
    void atTheStartOfARound() {
        assertSameAsTheHumanView(table.start(3, "KS 7H JC 9S 8H QC 4S 2H 3C"));
    }

    @Test
    void midRoundWithAPileAndAReversal() throws Exception {
        NinetyNine game = table.start(3, "4S 7H JC 9S 8H QC 3D 2H 3C 5D 6D");
        table.actions.play(game.getId(), new PlayerId(0), card("4S"), null);
        table.actions.play(game.getId(), new PlayerId(2), card("JC"), null);

        assertSameAsTheHumanView(game);
    }

    @Test
    void afterABustAndAfterTheNextDeal() throws Exception {
        NinetyNine game = table.beforeABust();
        table.actions.play(game.getId(), new PlayerId(0), card("KS"), null);
        table.actions.play(game.getId(), new PlayerId(1), card("7H"), null);
        assertSameAsTheHumanView(game);

        table.actions.nextRound(game.getId(), game.version());
        assertSameAsTheHumanView(game);
    }

    @Test
    void onceTheGameIsOver() {
        NinetyNine game = table.start(3, "KS 7H JC 9S 8H QC 4S 2H 3C");
        game.forfeit(new PlayerId(1));
        game.forfeit(new PlayerId(2));

        assertSameAsTheHumanView(game);
    }
}
