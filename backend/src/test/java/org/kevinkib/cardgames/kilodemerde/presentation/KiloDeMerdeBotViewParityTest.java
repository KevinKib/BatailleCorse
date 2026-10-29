package org.kevinkib.cardgames.kilodemerde.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeBotView;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.KiloDeMerdeDto;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import org.kevinkib.cards.domain.Card;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.ALL_SETS;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.NO_SET;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.card;

/**
 * The bot sees exactly what a human viewer at its seat receives: the domain view is compared with the seat's
 * {@code KiloDeMerdeDto}, field for field, in every phase and for every seat.
 */
class KiloDeMerdeBotViewParityTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);
    private static final PlayerId S2 = new PlayerId(2);

    private final KiloDeMerdeTable table = new KiloDeMerdeTable();

    private static List<CardDto> dtos(List<Card> cards) {
        return cards.stream().map(CardDto::from).toList();
    }

    private static void assertSameAsTheHumanView(KiloDeMerde game) {
        Map<PlayerId, String> names = new HashMap<>();
        game.getPlayerIds().forEach(p -> names.put(p, "Seat " + p.id()));
        for (PlayerId viewer : game.getPlayerIds()) {
            KiloDeMerdeBotView view = KiloDeMerdeBotView.forSeat(game, viewer);
            KiloDeMerdeDto human = KiloDeMerdeDto.forViewer(game, viewer, names, Set.of());
            String context = game.phase() + " round " + game.round() + " step " + game.step() + " seat " + viewer.id();

            assertThat(view.seat()).as(context).isEqualTo(viewer);
            assertThat(view.version()).as(context).isEqualTo(human.version());
            assertThat(view.round()).as(context).isEqualTo(human.round());
            assertThat(view.step()).as(context).isEqualTo(human.step());
            assertThat(view.phase().name()).as(context).isEqualTo(human.phase());
            assertThat(dtos(view.hand())).as(context).isEqualTo(human.hand());
            assertThat(view.pileSize()).as(context).isEqualTo(human.pileSize());
            assertThat(view.options().toGameOptions().values()).as(context).isEqualTo(human.options());
            assertThat(view.slapOrder().stream().map(PlayerId::id).toList()).as(context)
                    .isEqualTo(human.race() == null ? List.of() : human.race().order());
            for (KiloDeMerdeDto.PlayerDto player : human.players()) {
                PlayerId seat = new PlayerId(player.seat());
                assertThat(view.kilos().get(seat)).as(context).isEqualTo(player.kilos());
                assertThat(view.activeSeats().contains(seat)).as(context).isEqualTo(player.active());
            }
            // the seat's own pass history ends with the card the DTO calls received / given
            boolean playing = human.phase().equals("PASSING") || human.phase().equals("RACE");
            if (human.lastReceived() == null) {
                if (playing) {
                    assertThat(view.passHistory()).as(context).isEmpty();
                }
            } else {
                KiloDeMerdeBotView.Pass last = view.passHistory().get(view.passHistory().size() - 1);
                assertThat(CardDto.from(last.received())).as(context).isEqualTo(human.lastReceived().card());
                assertThat(CardDto.from(last.given())).as(context).isEqualTo(human.lastGiven().card());
                assertThat(last.step()).as(context).isEqualTo(human.step() - 1);
            }
        }
    }

    @Test
    void atTheStartOfARound() {
        assertSameAsTheHumanView(table.start(NO_SET));
    }

    @Test
    void afterAChoiceAndAResolvedStep() throws Exception {
        KiloDeMerde game = table.start(NO_SET);
        table.actions.choosePass(game.getId(), S0, card("QC"));
        assertSameAsTheHumanView(game);

        table.actions.choosePass(game.getId(), S1, card("QD"));
        table.actions.choosePass(game.getId(), S2, card("JC"));
        assertSameAsTheHumanView(game);
    }

    @Test
    void inARace() throws Exception {
        KiloDeMerde game = table.start(ALL_SETS);
        table.actions.slap(game.getId(), S1);

        assertSameAsTheHumanView(game);
    }

    @Test
    void afterTheRoundEndAndTheNextDeal() throws Exception {
        KiloDeMerde game = table.start(ALL_SETS);
        table.actions.slap(game.getId(), S1);
        table.actions.slap(game.getId(), S2);
        assertSameAsTheHumanView(game);

        table.nextRound(game, game.round());
        assertSameAsTheHumanView(game);
    }

    @Test
    void onceTheGameIsOver() {
        KiloDeMerde game = table.start(NO_SET);
        game.forfeit(S1);
        game.forfeit(S2);

        assertSameAsTheHumanView(game);
    }
}
