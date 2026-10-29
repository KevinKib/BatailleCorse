package org.kevinkib.cardgames.blackjack.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.blackjack.domain.Blackjack;
import org.kevinkib.cardgames.blackjack.domain.bot.BlackjackBotView;
import org.kevinkib.cardgames.blackjack.presentation.dto.BlackjackDto;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bot sees exactly what a human viewer at its seat receives: the domain view is compared with the seat's
 * {@code BlackjackDto}, field for field, in every phase and for the seat on turn and the others.
 */
class BlackjackBotViewParityTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);

    private final BlackjackTable table = new BlackjackTable();

    private static void assertSameAsTheHumanView(Blackjack game) {
        Map<PlayerId, String> names = new HashMap<>();
        game.getPlayerIds().forEach(p -> names.put(p, "Seat " + p.id()));
        for (PlayerId viewer : game.getPlayerIds()) {
            BlackjackBotView view = BlackjackBotView.forSeat(game, viewer);
            BlackjackDto human = BlackjackDto.forViewer(game, viewer, names, Set.of(), BotSeatOptions.none());
            String context = game.phase() + " hand " + game.handNumber() + " seat " + viewer.id();

            assertThat(view.seat()).as(context).isEqualTo(viewer);
            assertThat(view.version()).as(context).isEqualTo(human.version());
            assertThat(view.handNumber()).as(context).isEqualTo(human.handNumber());
            assertThat(view.handsLeft()).as(context).isEqualTo(human.handsTotal() - human.handNumber() + 1);
            assertThat(view.phase().name()).as(context).isEqualTo(human.phase());
            assertThat(view.activeHandIndex()).as(context).isEqualTo(human.activeHandIndex());
            assertThat(view.availableActions().stream().map(Enum::name).toList())
                    .as(context).isEqualTo(human.availableActions());
            assertThat(view.minBet()).as(context).isEqualTo(human.betLimits() == null ? 0 : human.betLimits().min());
            assertThat(view.maxBet()).as(context).isEqualTo(human.betLimits() == null ? 0 : human.betLimits().max());
            assertThat(view.button().id()).as(context).isEqualTo(human.button());
            assertThat(view.solo()).as(context).isEqualTo(human.solo());
            assertThat(view.options().hands().key()).as(context).isEqualTo(human.options().hands());
            assertThat(view.options().maxBet().key()).as(context).isEqualTo(human.options().maxBet());

            BlackjackDto.PlayerDto mine = human.players().get(viewer.id());
            assertThat(view.hands()).as(context).hasSameSizeAs(mine.hands());
            for (int i = 0; i < mine.hands().size(); i++) {
                BlackjackBotView.Hand hand = view.hands().get(i);
                BlackjackDto.HandDto dto = mine.hands().get(i);
                assertThat(hand.total()).as(context).isEqualTo(dto.total());
                assertThat(hand.soft()).as(context).isEqualTo(dto.soft());
                assertThat(hand.doubled()).as(context).isEqualTo(dto.doubled());
                if (dto.cards().size() != 2) {
                    assertThat(hand.pair()).as(context).isFalse();
                } else if (dto.cards().get(0).rank().equals(dto.cards().get(1).rank())) {
                    assertThat(hand.pair()).as(context).isTrue();
                }
            }

            Map<PlayerId, Integer> liveStacks = new LinkedHashMap<>();
            human.players().stream().filter(p -> !p.eliminated())
                    .forEach(p -> liveStacks.put(new PlayerId(p.seat()), p.stack()));
            assertThat(view.stacks()).as(context).isEqualTo(liveStacks);

            if (human.dealer().upCard() == null) {
                assertThat(view.dealerUpValue()).as(context).isZero();
            } else {
                assertThat(view.dealerUpValue()).as(context)
                        .isEqualTo(human.dealer().holeHidden() ? human.dealer().total()
                                : view.dealerUpValue());
            }
        }
    }

    @Test
    void atTheStartOfTheBetting() {
        assertSameAsTheHumanView(table.start(3, "10S 9H 5S 6D 7C 8C 9S 5H"));
    }

    @Test
    void afterOneBetAndOnceDealt() throws Exception {
        Blackjack game = table.start(2, "10S 9H 6D 7C 8C 5H 10D 3H");
        game.bet(S1, 25);
        assertSameAsTheHumanView(game);
        assertThat(BlackjackBotView.forSeat(game, S0).stacks()).containsEntry(S1, 100); // the bet is sealed
        game.bet(S0, 10);
        assertSameAsTheHumanView(game);
    }

    @Test
    void withAPairASplitAndADouble() throws Exception {
        Blackjack game = table.start(2, "8S 9H 6D 8C 8D 5H 3H 4S 10C 2S 2D");
        game.bet(S0, 10);
        game.bet(S1, 10);
        assertSameAsTheHumanView(game);
        assertThat(BlackjackBotView.forSeat(game, S0).hands().get(0).pair()).isTrue();
        game.split(S0);
        assertSameAsTheHumanView(game);
        game.doubleDown(S0);
        assertSameAsTheHumanView(game);
    }

    @Test
    void anAceUpCardCountsEleven() throws Exception {
        Blackjack game = table.start(1, "10S AH 7C 8H");
        game.bet(S0, 10);

        assertThat(BlackjackBotView.forSeat(game, S0).dealerUpValue()).isEqualTo(11);
        assertSameAsTheHumanView(game);
    }

    @Test
    void afterASettlementAndOnceTheGameIsOver() throws Exception {
        Blackjack game = table.start(1, GameOptions.of(Map.of("maxBet", "100")), "10S 6D 7C 5H 10D");
        game.bet(S0, 100);
        game.stand(S0);
        assertSameAsTheHumanView(game);
    }

    @Test
    void theNextBettingKeepsTheTableCleared() throws Exception {
        Blackjack game = table.start(2, "10S 9H 6D 7C 8C 5H 10D 3H");
        game.bet(S0, 10);
        game.bet(S1, 10);
        game.stand(S0);
        game.stand(S1);
        assertSameAsTheHumanView(game);
        assertThat(BlackjackBotView.forSeat(game, S0).handsLeft()).isEqualTo(9);
        assertThat(BlackjackBotView.forSeat(game, S0).dealerUpValue()).isZero();
    }
}
