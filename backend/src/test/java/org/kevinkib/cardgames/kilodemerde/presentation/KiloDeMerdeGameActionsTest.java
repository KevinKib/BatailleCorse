package org.kevinkib.cardgames.kilodemerde.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.AlreadySlappedException;
import org.kevinkib.cardgames.kilodemerde.domain.CardNotInHandException;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.domain.Phase;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.KiloDeMerdeDto;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.event.KiloDeMerdeEventData;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.CardDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.ALL_SETS;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.NO_SET;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.NO_SET_4;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.card;

class KiloDeMerdeGameActionsTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);
    private static final PlayerId S2 = new PlayerId(2);

    private final KiloDeMerdeTable table = new KiloDeMerdeTable();

    /** The event type of each distinct broadcast (every event goes to the three seats, one message each). */
    private List<String> eventTypes() {
        List<String> all = table.messaging.payloads.stream().map(Response::getEventType).toList();
        return java.util.stream.IntStream.range(0, all.size()).filter(i -> i % 3 == 0).mapToObj(all::get).toList();
    }

    @Test
    void aChoiceBroadcastsOnePassChosenEventToEverySeatWithItsOwnState() throws Exception {
        KiloDeMerde game = table.start(NO_SET);

        table.actions.choosePass(game.getId(), S0, card("QC"));

        assertThat(table.messaging.seats).containsExactly(S0, S1, S2);
        Response first = table.messaging.payloads.get(0);
        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getEventType()).isEqualTo("PASS_CHOSEN");
        assertThat(((KiloDeMerdeEventData) first.getEventData()).seat()).isZero();
        assertThat(((KiloDeMerdeDto) first.getState()).myChoice()).isEqualTo(CardDto.from(card("QC")));
        assertThat(((KiloDeMerdeDto) table.messaging.payloads.get(1).getState()).myChoice()).isNull();
    }

    @Test
    void theLastChoiceAnnouncesTheStep() throws Exception {
        KiloDeMerde game = table.start(NO_SET);
        table.actions.choosePass(game.getId(), S0, card("QC"));
        table.actions.choosePass(game.getId(), S1, card("QD"));
        table.messaging.clear();

        table.actions.choosePass(game.getId(), S2, card("JC"));

        assertThat(eventTypes()).containsExactly("PASS_CHOSEN", "STEP");
        KiloDeMerdeEventData step = (KiloDeMerdeEventData) table.messaging.payloads.get(3).getEventData();
        assertThat(step.round()).isEqualTo(1);
        assertThat(step.step()).isEqualTo(2);
        assertThat(((KiloDeMerdeDto) table.messaging.payloads.get(3).getState()).step()).isEqualTo(2);
    }

    @Test
    void aValidSlapOpensTheRaceWithTwoEvents() throws Exception {
        KiloDeMerde game = table.start(ALL_SETS);

        table.actions.slap(game.getId(), S1);

        assertThat(eventTypes()).containsExactly("RACE", "SLAP");
        assertThat(((KiloDeMerdeEventData) table.messaging.payloads.get(0).getEventData()).seat()).isEqualTo(1);
        assertThat(((KiloDeMerdeEventData) table.messaging.payloads.get(3).getEventData()).rank()).isEqualTo(1);
    }

    @Test
    void theLastSlapEndsTheRaceWithADrawAndTheRevealedHands() throws Exception {
        KiloDeMerde game = table.start(ALL_SETS);
        table.actions.slap(game.getId(), S1);
        table.messaging.clear();

        table.actions.slap(game.getId(), S2);

        assertThat(eventTypes()).containsExactly("SLAP", "DRAW", "ROUND_OVER");
        KiloDeMerdeEventData draw = (KiloDeMerdeEventData) table.messaging.payloads.get(3).getEventData();
        assertThat(draw.seat()).isZero();
        assertThat(draw.card()).isEqualTo(CardDto.from(card("AS")));
        assertThat(draw.kilosAfter()).isEqualTo(100);
        KiloDeMerdeEventData over = (KiloDeMerdeEventData) table.messaging.payloads.get(6).getEventData();
        assertThat(over.outcome().kind()).isEqualTo("RACE");
        assertThat(over.outcome().hands()).hasSize(3);
    }

    @Test
    void aFalseSlapAnnouncesItsOwnEvent() throws Exception {
        KiloDeMerde game = table.start(NO_SET);

        table.actions.slap(game.getId(), S0);

        assertThat(eventTypes()).containsExactly("FALSE_SLAP", "DRAW", "ROUND_OVER");
        assertThat(((KiloDeMerdeEventData) table.messaging.payloads.get(0).getEventData()).seat()).isZero();
    }

    @Test
    void aSevenIsAFlush() throws Exception {
        KiloDeMerde game = table.start(3, NO_SET, "7D AS 9C");

        table.actions.slap(game.getId(), S0);

        assertThat(eventTypes()).containsExactly("FALSE_SLAP", "FLUSH", "ROUND_OVER");
        assertThat(((KiloDeMerdeEventData) table.messaging.payloads.get(3).getEventData()).returnedCount()).isEqualTo(1);
    }

    @Test
    void theEndOfTheGameCarriesTheResult() throws Exception {
        KiloDeMerde game = table.start(3, org.kevinkib.cardgames.game.GameOptions.of(java.util.Map.of("rounds", "5")),
                NO_SET, KiloDeMerdeTable.PILE);
        for (int round = 1; round <= 5; round++) {
            table.actions.slap(game.getId(), S0);
            table.decks.fixDeal(game.getId(), KiloDeMerdeTable.hands(NO_SET), List.of());
            table.actions.nextRound(game.getId(), game, game.round());
        }

        assertThat(game.phase()).isEqualTo(Phase.FINISHED);
        Response last = table.messaging.payloads.get(table.messaging.payloads.size() - 1);
        assertThat(last.getEventType()).isEqualTo("GAME_OVER");
        KiloDeMerdeEventData data = (KiloDeMerdeEventData) last.getEventData();
        assertThat(data.result().reason()).isEqualTo("ROUNDS_PLAYED");
    }

    @Test
    void aRefusedCommandBroadcastsNothingAndThrows() throws Exception {
        KiloDeMerde game = table.start(ALL_SETS);
        table.actions.slap(game.getId(), S1);
        table.messaging.clear();
        int version = game.version();

        assertThatThrownBy(() -> table.actions.slap(game.getId(), S1)).isInstanceOf(AlreadySlappedException.class);
        assertThatThrownBy(() -> table.actions.choosePass(game.getId(), S0, card("2S")))
                .isInstanceOf(org.kevinkib.cardgames.kilodemerde.domain.WrongPhaseException.class);

        assertThat(table.messaging.payloads).isEmpty();
        assertThat(game.version()).isEqualTo(version);
    }

    @Test
    void aCardNotInHandIsRefused() throws Exception {
        KiloDeMerde game = table.start(NO_SET);

        assertThatThrownBy(() -> table.actions.choosePass(game.getId(), S0, card("2S")))
                .isInstanceOf(CardNotInHandException.class);
        assertThat(table.messaging.payloads).isEmpty();
    }

    // ---- timer entry points: stale calls do nothing ----

    @Test
    void thePassTimerPassesForTheSeatsThatDidNotChoose() throws Exception {
        KiloDeMerde game = table.start(NO_SET);
        table.actions.choosePass(game.getId(), S0, card("QC"));
        table.messaging.clear();

        table.actions.expirePassTimer(game.getId(), game, KiloDeMerdeStepKey.of(game));

        assertThat(game.step()).isEqualTo(2);
        assertThat(eventTypes()).containsExactly("PASS_CHOSEN", "PASS_CHOSEN", "STEP");
    }

    @Test
    void aPassTimerOfAnEarlierStepDoesNothing() throws Exception {
        KiloDeMerde game = table.start(NO_SET);
        KiloDeMerdeStepKey stale = KiloDeMerdeStepKey.of(game);
        table.actions.choosePass(game.getId(), S0, card("QC"));
        table.actions.choosePass(game.getId(), S1, card("QD"));
        table.actions.choosePass(game.getId(), S2, card("JC"));
        table.messaging.clear();

        table.actions.expirePassTimer(game.getId(), game, stale);

        assertThat(game.step()).isEqualTo(2);
        assertThat(table.messaging.payloads).isEmpty();
    }

    @Test
    void aPassTimerOfAVoidedDealDoesNotHitTheNewDealAtTheSameRoundAndStep() throws Exception {
        KiloDeMerde game = table.start(4, NO_SET_4, KiloDeMerdeTable.PILE);
        KiloDeMerdeStepKey beforeTheForfeit = KiloDeMerdeStepKey.of(game);
        game.forfeit(S1); // the round is voided and redealt: still round 1, step 1
        table.messaging.clear();

        assertThat(KiloDeMerdeStepKey.of(game)).isNotEqualTo(beforeTheForfeit);
        table.actions.expirePassTimer(game.getId(), game, beforeTheForfeit);

        assertThat(game.step()).isEqualTo(1);
        assertThat(table.messaging.payloads).isEmpty();
    }

    @Test
    void aPassTimerOfAnotherGameObjectUnderTheSameIdDoesNothing() throws Exception {
        KiloDeMerde game = table.start(NO_SET);
        KiloDeMerde stranger = new KiloDeMerde(game.getId(), 3, table.decks,
                org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeOptions.defaults());
        table.messaging.clear();

        table.actions.expirePassTimer(game.getId(), stranger, KiloDeMerdeStepKey.of(stranger));

        assertThat(game.step()).isEqualTo(1);
        assertThat(table.messaging.payloads).isEmpty();
    }

    @Test
    void theRaceTimeoutMakesTheNonSlappersDraw() throws Exception {
        KiloDeMerde game = table.start(ALL_SETS);
        table.actions.slap(game.getId(), S1);
        table.messaging.clear();

        table.actions.closeRace(game.getId(), game, game.raceId());

        assertThat(game.phase()).isEqualTo(Phase.ROUND_OVER);
        assertThat(eventTypes()).containsExactly("DRAW", "DRAW", "ROUND_OVER");
    }

    @Test
    void aRaceTimeoutOfAnotherRaceOrOutsideARaceDoesNothing() throws Exception {
        KiloDeMerde game = table.start(ALL_SETS);
        table.actions.slap(game.getId(), S1);
        table.messaging.clear();

        table.actions.closeRace(game.getId(), game, game.raceId() + 1);
        assertThat(game.phase()).isEqualTo(Phase.RACE);

        table.actions.slap(game.getId(), S2);
        table.messaging.clear();
        table.actions.closeRace(game.getId(), game, game.raceId());
        assertThat(table.messaging.payloads).isEmpty();
    }

    @Test
    void theRoundPauseDealsTheNextRoundOnce() throws Exception {
        KiloDeMerde game = table.start(NO_SET);
        table.actions.slap(game.getId(), S0);
        table.messaging.clear();

        table.actions.nextRound(game.getId(), game, 1);

        assertThat(game.round()).isEqualTo(2);
        assertThat(eventTypes()).containsExactly("ROUND_START");
        table.messaging.clear();
        table.actions.nextRound(game.getId(), game, 1); // stale: round 2 is being played
        assertThat(table.messaging.payloads).isEmpty();
        assertThat(game.round()).isEqualTo(2);
    }

    @Test
    void whatAForfeitCausedIsAnnouncedAfterwards() throws Exception {
        KiloDeMerde four = table.start(4, NO_SET_4, KiloDeMerdeTable.PILE);
        four.forfeit(S1);
        table.messaging.clear();

        table.actions.announceForfeitAftermath(four, "Player 1 forfeited.");

        assertThat(table.messaging.payloads.stream().map(Response::getEventType).distinct().toList())
                .containsExactly("ROUND_START"); // one message to each of the four seats

        KiloDeMerde three = table.start(NO_SET);
        three.forfeit(S1);
        table.messaging.clear();

        table.actions.announceForfeitAftermath(three, "Player 1 forfeited.");

        assertThat(eventTypes()).containsExactly("GAME_OVER");
    }
}
