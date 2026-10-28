package org.kevinkib.cardgames.kilodemerde.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerde;
import org.kevinkib.cardgames.kilodemerde.presentation.dto.KiloDeMerdeDto;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.ALL_SETS;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.NO_SET;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.NO_SET_4;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.card;
import static org.kevinkib.cardgames.kilodemerde.presentation.KiloDeMerdeTable.cards;

/** The per-seat projection: public facts for everyone, hands, choices and received cards for their owner only. */
class KiloDeMerdeDtoTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);
    private static final PlayerId S2 = new PlayerId(2);

    private final KiloDeMerdeTable table = new KiloDeMerdeTable();
    private final JsonMapper json = JsonMapper.builder().build();

    private static List<CardDto> dtos(String codes) {
        return cards(codes).stream().map(CardDto::from).toList();
    }

    private static KiloDeMerdeDto view(KiloDeMerde game, PlayerId viewer) {
        Map<PlayerId, String> names = new HashMap<>();
        game.getPlayerIds().forEach(p -> names.put(p, "Seat " + p.id()));
        return KiloDeMerdeDto.forViewer(game, viewer, names, Set.of());
    }

    @Test
    void theStartShowsTheTablePubliclyAndTheViewersHandOnly() throws Exception {
        KiloDeMerde game = table.start(NO_SET);

        KiloDeMerdeDto dto = view(game, S1);

        assertThat(dto.started()).isTrue();
        assertThat(dto.gameType()).isEqualTo("kilo-de-merde");
        assertThat(dto.id()).isEqualTo(game.getId().uuid().toString());
        assertThat(dto.round()).isEqualTo(1);
        assertThat(dto.roundsTotal()).isEqualTo(10);
        assertThat(dto.step()).isEqualTo(1);
        assertThat(dto.phase()).isEqualTo("PASSING");
        assertThat(dto.pileSize()).isEqualTo(5);
        assertThat(dto.options()).containsEntry("rounds", "10");
        assertThat(dto.players()).hasSize(3);
        assertThat(dto.players()).allSatisfy(p -> {
            assertThat(p.kilos()).isZero();
            assertThat(p.kiloCards()).isEmpty();
            assertThat(p.chosen()).isFalse();
            assertThat(p.slapRank()).isNull();
            assertThat(p.active()).isTrue();
            assertThat(p.place()).isNull();
        });
        assertThat(dto.players().get(2).name()).isEqualTo("Seat 2");
        assertThat(dto.hand()).isEqualTo(dtos("JS JH QD QH"));
        assertThat(dto.myChoice()).isNull();
        assertThat(dto.lastReceived()).isNull();
        assertThat(dto.lastGiven()).isNull();
        assertThat(dto.race()).isNull();
        assertThat(dto.lastOutcome()).isNull();
        assertThat(dto.result()).isNull();
        assertThat(dto.availableActions()).containsExactly("PASS", "SLAP");
        assertThat(dto.passTimerEndsAt()).isNull();
        assertThat(dto.raceEndsAt()).isNull();
    }

    @Test
    void noForeignHandAndNoPileOrderReachesTheJsonOfASeat() throws Exception {
        KiloDeMerde game = table.start(NO_SET);

        String seat1 = json.writeValueAsString(view(game, S1));

        assertThat(seat1).contains("JACK").doesNotContain("KING");
        assertThat(seat1).doesNotContain("HEART_KING", "CLUB_QUEEN", "SPADE_QUEEN", "DIAMOND_JACK");
        assertThat(seat1).doesNotContain("SPADE_ACE", "CLUB_NINE"); // the pile order stays on the server
    }

    @Test
    void aChoiceIsPublicAsAMarkAndPrivateAsACard() throws Exception {
        KiloDeMerde game = table.start(NO_SET);
        table.actions.choosePass(game.getId(), S0, card("QC"));

        KiloDeMerdeDto mine = view(game, S0);
        KiloDeMerdeDto theirs = view(game, S1);

        assertThat(mine.myChoice()).isEqualTo(CardDto.from(card("QC")));
        assertThat(mine.availableActions()).containsExactly("SLAP");
        assertThat(theirs.myChoice()).isNull();
        assertThat(theirs.players().get(0).chosen()).isTrue();
        assertThat(theirs.players().get(1).chosen()).isFalse();
        assertThat(json.writeValueAsString(theirs)).doesNotContain("CLUB_QUEEN");
    }

    @Test
    void theReceivedAndGivenCardsAreTheSeatsOwnAfterAStep() throws Exception {
        KiloDeMerde game = table.start(NO_SET);
        table.actions.choosePass(game.getId(), S0, card("QC"));
        table.actions.choosePass(game.getId(), S1, card("QD"));
        table.actions.choosePass(game.getId(), S2, card("JC"));

        KiloDeMerdeDto dto = view(game, S1);

        assertThat(dto.step()).isEqualTo(2);
        assertThat(dto.hand()).isEqualTo(dtos("JS JH QC QH"));
        assertThat(dto.lastReceived()).isEqualTo(new KiloDeMerdeDto.ReceivedDto(CardDto.from(card("QC")), 0));
        assertThat(dto.lastGiven()).isEqualTo(new KiloDeMerdeDto.GivenDto(CardDto.from(card("QD")), 2));
        assertThat(dto.myChoice()).isNull();
        assertThat(dto.players()).allSatisfy(p -> assertThat(p.chosen()).isFalse());
        String raw = json.writeValueAsString(dto);
        assertThat(raw).doesNotContain("CLUB_JACK", "KING");
    }

    @Test
    void aRaceShowsTheSlapOrderAndWhoMayStillSlap() throws Exception {
        KiloDeMerde game = table.start(ALL_SETS);
        table.actions.slap(game.getId(), S1);

        KiloDeMerdeDto first = view(game, S1);
        KiloDeMerdeDto other = view(game, S2);

        assertThat(first.phase()).isEqualTo("RACE");
        assertThat(first.race()).isEqualTo(new KiloDeMerdeDto.RaceDto(1, List.of(1)));
        assertThat(first.players().get(1).slapRank()).isEqualTo(1);
        assertThat(first.players().get(0).slapRank()).isNull();
        assertThat(first.availableActions()).isEmpty();
        assertThat(other.availableActions()).containsExactly("SLAP");
        assertThat(other.lastOutcome()).isNull();
    }

    @Test
    void theRoundEndRevealsEveryHandAndTheDraw() throws Exception {
        KiloDeMerde game = table.start(ALL_SETS);
        table.actions.slap(game.getId(), S1);
        table.actions.slap(game.getId(), S2);

        KiloDeMerdeDto dto = view(game, S2);

        assertThat(dto.phase()).isEqualTo("ROUND_OVER");
        assertThat(dto.players().get(0).kilos()).isEqualTo(100);
        assertThat(dto.players().get(0).kiloCards()).isEqualTo(dtos("AS"));
        assertThat(dto.pileSize()).isEqualTo(4);
        assertThat(dto.availableActions()).isEmpty();
        KiloDeMerdeDto.OutcomeDto outcome = dto.lastOutcome();
        assertThat(outcome.kind()).isEqualTo("RACE");
        assertThat(outcome.firstSlapper()).isEqualTo(1);
        assertThat(outcome.slapOrder()).containsExactly(1, 2);
        assertThat(outcome.holders()).containsExactly(0, 1, 2);
        assertThat(outcome.draws()).containsExactly(new KiloDeMerdeDto.DrawDto(0, CardDto.from(card("AS")), false, 100));
        assertThat(outcome.hands()).containsExactly(
                new KiloDeMerdeDto.HandDto(0, dtos("KS KH KD KC")),
                new KiloDeMerdeDto.HandDto(1, dtos("JS JH JD JC")),
                new KiloDeMerdeDto.HandDto(2, dtos("QS QH QD QC")));
    }

    @Test
    void aFalseSlapIsAnOutcomeOfItsOwnKind() throws Exception {
        KiloDeMerde game = table.start(NO_SET);
        table.actions.slap(game.getId(), S0);

        KiloDeMerdeDto.OutcomeDto outcome = view(game, S1).lastOutcome();

        assertThat(outcome.kind()).isEqualTo("FALSE_SLAP");
        assertThat(outcome.holders()).isEmpty();
        assertThat(outcome.draws()).hasSize(1);
        assertThat(outcome.draws().get(0).seat()).isZero();
    }

    @Test
    void theNextDealForgetsTheOutcomeAndTheReceivedCard() throws Exception {
        KiloDeMerde game = table.start(NO_SET);
        table.actions.slap(game.getId(), S0);
        table.actions.nextRound(game.getId(), game, game.round());

        KiloDeMerdeDto dto = view(game, S0);

        assertThat(dto.round()).isEqualTo(2);
        assertThat(dto.step()).isEqualTo(1);
        assertThat(dto.lastOutcome()).isNull();
        assertThat(dto.lastReceived()).isNull();
    }

    @Test
    void aFinishedGameCarriesTheStandings() throws Exception {
        KiloDeMerde game = table.start(NO_SET);
        game.forfeit(S1); // two seats are left: below the minimum, the game ends at once

        KiloDeMerdeDto dto = view(game, S0);

        assertThat(dto.phase()).isEqualTo("FINISHED");
        assertThat(dto.result().reason()).isEqualTo("FORFEIT");
        assertThat(dto.result().standings()).extracting(KiloDeMerdeDto.StandingDto::seat).containsExactly(0, 1, 2);
        assertThat(dto.result().standings()).extracting(KiloDeMerdeDto.StandingDto::place).containsExactly(1, 3, 1);
        assertThat(dto.players().get(0).place()).isEqualTo(1);
        assertThat(dto.players().get(1).active()).isFalse();
        assertThat(dto.availableActions()).isEmpty();
    }

    @Test
    void aVoidedRoundKeepsItsNumberAndRestartsAtStepOne() throws Exception {
        KiloDeMerde game = table.start(4, NO_SET_4, KiloDeMerdeTable.PILE);
        game.forfeit(S1);

        KiloDeMerdeDto dto = view(game, S0);

        assertThat(dto.players()).hasSize(4);
        assertThat(dto.players().get(1).active()).isFalse();
        assertThat(dto.round()).isEqualTo(1); // a voided round keeps its number
        assertThat(dto.step()).isEqualTo(1);
    }

    @Test
    void aBotSeatIsFlaggedWithItsLevelAndOthersAreNot() throws Exception {
        KiloDeMerde game = table.start(NO_SET);
        Map<PlayerId, String> names = new HashMap<>();
        game.getPlayerIds().forEach(p -> names.put(p, p.id() == 1 ? null : "Seat " + p.id()));

        KiloDeMerdeDto dto = KiloDeMerdeDto.forViewer(game, S0, names, Set.of(S1),
                (id, seat) -> Map.of("level", "hard"), KiloDeMerdeDeadlines.off());

        assertThat(dto.players().get(1).bot()).isTrue();
        assertThat(dto.players().get(1).level()).isEqualTo("hard");
        assertThat(dto.players().get(1).name()).isNull();
        assertThat(dto.players().get(0).bot()).isFalse();
        assertThat(dto.players().get(0).level()).isNull();
    }

    @Test
    void theOptionsAreThoseOfTheRoom() throws Exception {
        KiloDeMerde game = table.start(3, GameOptions.of(Map.of("rounds", "5")), NO_SET, KiloDeMerdeTable.PILE);

        assertThat(view(game, S0).roundsTotal()).isEqualTo(5);
        assertThat(view(game, S0).options()).containsEntry("rounds", "5");
    }

    @Test
    void theDeadlinesComeFromTheTimersAndStayStableWithinAStep() throws Exception {
        long[] now = {1_000_000};
        KiloDeMerdeDeadlines deadlines = new KiloDeMerdeDeadlines(new MutableClock(now), 15_000, 5_000);
        KiloDeMerdeTable timed = new KiloDeMerdeTable(deadlines);
        KiloDeMerde game = timed.start(ALL_SETS);

        Long first = view2(timed, game, S0).passTimerEndsAt();
        now[0] += 2_000;
        Long again = view2(timed, game, S1).passTimerEndsAt();

        assertThat(first).isEqualTo(1_015_000L);
        assertThat(again).isEqualTo(first); // same step: the same deadline for every seat and every projection
        assertThat(view2(timed, game, S0).raceEndsAt()).isNull();

        timed.actions.slap(game.getId(), S1);
        assertThat(view2(timed, game, S0).passTimerEndsAt()).isNull();
        assertThat(view2(timed, game, S0).raceEndsAt()).isEqualTo(1_007_000L);
    }

    private static KiloDeMerdeDto view2(KiloDeMerdeTable timed, KiloDeMerde game, PlayerId viewer) {
        return timed.broadcaster.stateFor(game, viewer);
    }

    /** A clock the test moves by hand. */
    private static final class MutableClock extends java.time.Clock {
        private final long[] now;

        MutableClock(long[] now) {
            this.now = now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return java.time.Instant.ofEpochMilli(now[0]);
        }
    }
}
