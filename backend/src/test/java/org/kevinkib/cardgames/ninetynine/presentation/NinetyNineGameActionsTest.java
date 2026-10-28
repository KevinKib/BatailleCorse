package org.kevinkib.cardgames.ninetynine.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.AceValueException;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.domain.NotPlayersTurnException;
import org.kevinkib.cardgames.ninetynine.domain.UnsafeCardException;
import org.kevinkib.cardgames.ninetynine.presentation.dto.NinetyNineDto;
import org.kevinkib.cardgames.ninetynine.presentation.dto.event.NinetyNineEventData;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.CardDto;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kevinkib.cardgames.ninetynine.presentation.NinetyNineTable.card;
import static org.kevinkib.cardgames.ninetynine.presentation.NinetyNineTable.cards;

class NinetyNineGameActionsTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);
    private static final PlayerId S2 = new PlayerId(2);

    private final NinetyNineTable table = new NinetyNineTable();

    private List<String> eventTypes() {
        return table.messaging.payloads.stream().map(Response::getEventType).toList();
    }

    @Test
    void aPlayBroadcastsOnePlayEventToEverySeatWithItsOwnState() throws Exception {
        NinetyNine game = table.start(3, "9S 7H JC 4S 8H QC 5S 2H 3C 5D");

        table.actions.play(game.getId(), S0, card("9S"), null);

        assertThat(table.messaging.seats).containsExactly(S0, S1, S2);
        Response first = table.messaging.payloads.get(0);
        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getEventType()).isEqualTo("PLAY");
        NinetyNineEventData data = (NinetyNineEventData) first.getEventData();
        assertThat(data.seat()).isZero();
        assertThat(data.card()).isEqualTo(CardDto.from(card("9S")));
        assertThat(data.aceValue()).isNull();
        assertThat(data.totalAfter()).isZero();
        assertThat(data.reversed()).isFalse();
        NinetyNineDto state = (NinetyNineDto) first.getState();
        assertThat(state.currentSeat()).isEqualTo(1);
        assertThat(state.hand()).isEqualTo(cards("4S 5S 5D").stream().map(CardDto::from).toList());
        assertThat(((NinetyNineDto) table.messaging.payloads.get(1).getState()).hand())
                .isEqualTo(cards("7H 8H 2H").stream().map(CardDto::from).toList());
    }

    @Test
    void anAceCarriesTheValueTheSeatChose() throws Exception {
        NinetyNine game = table.start(2, "AS 10H 5C 9H 4S 7H 3C");

        table.actions.play(game.getId(), S0, card("AS"), 11);

        NinetyNineEventData data = (NinetyNineEventData) table.messaging.payloads.get(0).getEventData();
        assertThat(data.aceValue()).isEqualTo(11);
        assertThat(data.totalAfter()).isEqualTo(11);
    }

    @Test
    void aFourSaysItReversedTheDirection() throws Exception {
        NinetyNine game = table.start(3, "4S 7H JC 9S 8H QC 5S 2H 3C 5D");

        table.actions.play(game.getId(), S0, card("4S"), null);

        assertThat(((NinetyNineEventData) table.messaging.payloads.get(0).getEventData()).reversed()).isTrue();
    }

    @Test
    void aPlayThatLeavesTheNextSeatStuckBroadcastsPlayThenBust() throws Exception {
        NinetyNine game = table.beforeABust();

        table.actions.play(game.getId(), S0, card("KS"), null);

        assertThat(eventTypes()).containsExactly("PLAY", "PLAY", "PLAY", "BUST", "BUST", "BUST");
        NinetyNineEventData bust = (NinetyNineEventData) table.messaging.payloads.get(3).getEventData();
        assertThat(bust.seat()).isEqualTo(1);
        assertThat(bust.hand()).isEqualTo(cards("7H 8H 2H").stream().map(CardDto::from).toList());
        assertThat(bust.livesLeft()).isEqualTo(2);
        assertThat(bust.card()).isNull();
        NinetyNineDto state = (NinetyNineDto) table.messaging.payloads.get(3).getState();
        assertThat(state.phase()).isEqualTo("ROUND_OVER");
    }

    @Test
    void theBustThatEndsTheGameAddsEliminatedAndGameOver() throws Exception {
        NinetyNine game = table.start(2, GameOptions.of(Map.of("lives", "2")),
                "KS 7H 9S 8H 4S 2H 5D", "KD 9D 5D 8D 6D 7D 2D 3D");
        table.actions.play(game.getId(), S0, card("KS"), null);
        table.actions.nextRound(game.getId(), game.version());
        table.actions.play(game.getId(), S1, card("9D"), null);
        table.messaging.clear();

        table.actions.play(game.getId(), S0, card("KD"), null);

        assertThat(eventTypes()).containsExactly("PLAY", "PLAY", "BUST", "BUST", "ELIMINATED", "ELIMINATED",
                "GAME_OVER", "GAME_OVER");
        NinetyNineEventData eliminated = (NinetyNineEventData) table.messaging.payloads.get(4).getEventData();
        assertThat(eliminated.seat()).isEqualTo(1);
        assertThat(eliminated.place()).isEqualTo(2);
        assertThat(((NinetyNineDto) table.messaging.payloads.get(6).getState()).phase()).isEqualTo("FINISHED");
    }

    @Test
    void nextRoundDealsAndBroadcastsRoundStartWithTheStarter() throws Exception {
        NinetyNine game = table.beforeABust();
        table.actions.play(game.getId(), S0, card("KS"), null);
        table.messaging.clear();

        table.actions.nextRound(game.getId(), game.version());

        assertThat(eventTypes()).containsExactly("ROUND_START", "ROUND_START", "ROUND_START");
        NinetyNineEventData data = (NinetyNineEventData) table.messaging.payloads.get(0).getEventData();
        assertThat(data.seat()).isEqualTo(1);
        NinetyNineDto state = (NinetyNineDto) table.messaging.payloads.get(0).getState();
        assertThat(state.round()).isEqualTo(2);
        assertThat(state.phase()).isEqualTo("PLAYING");
        assertThat(state.total()).isZero();
        assertThat(state.lastBust()).isNull();
    }

    @Test
    void nextRoundOfAStaleVersionOrOutsideTheRoundEndDoesNothing() throws Exception {
        NinetyNine game = table.beforeABust();
        table.actions.nextRound(game.getId(), game.version()); // still PLAYING
        table.actions.play(game.getId(), S0, card("KS"), null);
        int version = game.version();
        table.messaging.clear();

        table.actions.nextRound(game.getId(), version - 1);

        assertThat(table.messaging.payloads).isEmpty();
        assertThat(game.version()).isEqualTo(version);
        assertThat(game.round()).isEqualTo(1);
    }

    @Test
    void aRejectedCommandBroadcastsNothingAndPropagates() throws Exception {
        NinetyNine game = table.atNinetyFour();
        int version = game.version();

        assertThatThrownBy(() -> table.actions.play(game.getId(), S0, card("AS"), 1))
                .isInstanceOf(NotPlayersTurnException.class);
        assertThatThrownBy(() -> table.actions.play(game.getId(), S1, card("7H"), null))
                .isInstanceOf(UnsafeCardException.class);
        assertThatThrownBy(() -> table.actions.play(game.getId(), S1, card("AS"), null))
                .isInstanceOf(org.kevinkib.cardgames.ninetynine.domain.CardNotInHandException.class);

        assertThat(table.messaging.payloads).isEmpty();
        assertThat(game.version()).isEqualTo(version);
    }

    @Test
    void anAceWithoutAValueIsRefusedAndNothingIsBroadcast() {
        NinetyNine game = table.start(2, "AS 10H 5C 9H 4S 7H 3C");

        assertThatThrownBy(() -> table.actions.play(game.getId(), S0, card("AS"), null))
                .isInstanceOf(AceValueException.class);

        assertThat(table.messaging.payloads).isEmpty();
        assertThat(game.version()).isZero();
    }
}
