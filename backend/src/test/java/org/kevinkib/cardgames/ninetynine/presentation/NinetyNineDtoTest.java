package org.kevinkib.cardgames.ninetynine.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.NinetyNine;
import org.kevinkib.cardgames.ninetynine.presentation.dto.NinetyNineDto;
import org.kevinkib.cardgames.presentation.bot.BotSeatOptions;
import org.kevinkib.cardgames.presentation.dto.CardDto;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kevinkib.cardgames.ninetynine.presentation.NinetyNineTable.card;
import static org.kevinkib.cardgames.ninetynine.presentation.NinetyNineTable.cards;

class NinetyNineDtoTest {

    private static final PlayerId S0 = new PlayerId(0);
    private static final PlayerId S1 = new PlayerId(1);
    private static final PlayerId S2 = new PlayerId(2);

    private final NinetyNineTable table = new NinetyNineTable();

    private static NinetyNineDto view(NinetyNine game, PlayerId viewer) {
        return NinetyNineDto.forViewer(game, viewer, Map.of(S0, "Alice", S1, "Bob"), Set.of(), BotSeatOptions.none());
    }

    private static CardDto dto(String code) {
        return CardDto.from(card(code));
    }

    @Test
    void aFreshRoundShowsTheTablePublicly() throws Exception {
        NinetyNine game = table.start(3, "KS 7H JC 9S 8H QC 4S 2H 3C");

        NinetyNineDto dto = view(game, S0);

        assertThat(dto.started()).isTrue();
        assertThat(dto.id()).isEqualTo(game.getId().uuid().toString());
        assertThat(dto.gameType()).isEqualTo("ninety-nine");
        assertThat(dto.round()).isEqualTo(1);
        assertThat(dto.phase()).isEqualTo("PLAYING");
        assertThat(dto.total()).isZero();
        assertThat(dto.direction()).isEqualTo("CLOCKWISE");
        assertThat(dto.currentSeat()).isZero();
        assertThat(dto.pileTop()).isNull();
        assertThat(dto.pileCount()).isZero();
        assertThat(dto.stockSize()).isEqualTo(52 - 9);
        assertThat(dto.options()).containsEntry("lives", "3").containsEntry("quick", "off");
        assertThat(dto.lastPlay()).isNull();
        assertThat(dto.lastBust()).isNull();
        assertThat(dto.outcome()).isEqualTo(new NinetyNineDto.OutcomeDto.Ongoing());
        assertThat(dto.players()).extracting(NinetyNineDto.PlayerDto::seat).containsExactly(0, 1, 2);
        assertThat(dto.players()).allSatisfy(p -> {
            assertThat(p.lives()).isEqualTo(3);
            assertThat(p.handSize()).isEqualTo(3);
            assertThat(p.eliminated()).isFalse();
            assertThat(p.place()).isNull();
            assertThat(p.bot()).isFalse();
            assertThat(p.level()).isNull();
        });
        assertThat(dto.players().get(0).name()).isEqualTo("Alice");
        assertThat(dto.players().get(2).name()).isNull();
    }

    @Test
    void aSeatSeesItsOwnHandAndNeverAnotherHand() {
        NinetyNine game = table.start(3, "KS 7H JC 9S 8H QC 4S 2H 3C");

        NinetyNineDto mine = view(game, S1);

        assertThat(mine.hand()).containsExactlyElementsOf(cards("7H 8H 2H").stream().map(CardDto::from).toList());
        for (String foreign : new String[] {"KS", "9S", "4S", "JC", "QC", "3C"}) {
            assertThat(mine.toString()).doesNotContain(dto(foreign).name());
        }
        assertThat(mine.players()).allSatisfy(p -> assertThat(p.handSize()).isEqualTo(3));
    }

    @Test
    void theStockOrderIsNeverSent() {
        NinetyNine game = table.start(2, "KS 10H 5C 9H AS 7H 3C 6D 2C");

        NinetyNineDto dto = view(game, S0);

        for (String next : new String[] {"3C", "6D", "2C"}) {
            assertThat(dto.toString()).doesNotContain(dto(next).name());
        }
    }

    @Test
    void onlyTheSeatOnTurnHasThePlayAction() {
        NinetyNine game = table.start(3, "KS 7H JC 9S 8H QC 4S 2H 3C");

        assertThat(view(game, S0).availableActions()).containsExactly("PLAY");
        assertThat(view(game, S1).availableActions()).isEmpty();
        assertThat(view(game, S2).availableActions()).isEmpty();
    }

    @Test
    void playableTellsTheViewerWhichCardsAreSafeAndTheAceValuesAllowed() throws Exception {
        NinetyNine game = table.atNinetyFour();

        NinetyNineDto seat1 = view(game, S1);
        assertThat(seat1.total()).isEqualTo(94);
        assertThat(seat1.playable()).containsExactly(
                new NinetyNineDto.PlayableDto(dto("9H"), true, java.util.List.of()),
                new NinetyNineDto.PlayableDto(dto("7H"), false, java.util.List.of()),
                new NinetyNineDto.PlayableDto(dto("6D"), false, java.util.List.of()));

        NinetyNineDto seat0 = view(game, S0);
        assertThat(seat0.playable()).containsExactly(
                new NinetyNineDto.PlayableDto(dto("AS"), true, java.util.List.of(1)),
                new NinetyNineDto.PlayableDto(dto("3C"), true, java.util.List.of()),
                new NinetyNineDto.PlayableDto(dto("2C"), true, java.util.List.of()));
    }

    @Test
    void anAceOffersBothValuesWhileElevenStaysUnderTheLimit() {
        NinetyNine game = table.start(2, "KS 10H AS 9H 5C 7H");

        assertThat(view(game, S0).playable().get(1))
                .isEqualTo(new NinetyNineDto.PlayableDto(dto("AS"), true, java.util.List.of(1, 11)));
    }

    @Test
    void theLastPlayIsPublicWithTheTotalsBeforeAndAfter() throws Exception {
        NinetyNine game = table.atNinetyFour();

        NinetyNineDto dto = view(game, S1);

        assertThat(dto.lastPlay()).isEqualTo(new NinetyNineDto.LastPlayDto(0, dto("5C"), null, 89, 94, false));
        assertThat(dto.pileTop()).isEqualTo(dto("5C"));
        assertThat(dto.pileCount()).isEqualTo(3);
        assertThat(dto.stockSize()).isEqualTo(52 - 6 - 3);
        assertThat(dto.currentSeat()).isEqualTo(1);
    }

    @Test
    void aFourTurnsTheDirectionAndSaysSo() throws Exception {
        NinetyNine game = table.start(3, "4S 7H JC 9S 8H QC 3D 2H 3C 5D");

        table.actions.play(game.getId(), S0, card("4S"), null);

        NinetyNineDto dto = view(game, S0);
        assertThat(dto.direction()).isEqualTo("COUNTER_CLOCKWISE");
        assertThat(dto.lastPlay().reversed()).isTrue();
        assertThat(dto.currentSeat()).isEqualTo(2);
    }

    @Test
    void aStuckSeatIsAnnouncedToEveryoneAndItsViewerMayPlayAnyCard() throws Exception {
        NinetyNine game = table.beforeABust();
        assertThat(view(game, S0).stuck()).isFalse();

        table.actions.play(game.getId(), S0, card("KS"), null);

        for (PlayerId viewer : new PlayerId[] {S0, S1, S2}) {
            NinetyNineDto dto = view(game, viewer);
            assertThat(dto.stuck()).isTrue();
            assertThat(dto.phase()).isEqualTo("PLAYING");
            assertThat(dto.currentSeat()).isEqualTo(1);
            assertThat(dto.lastBust()).isNull();
        }
        assertThat(view(game, S1).playable()).containsExactly(
                new NinetyNineDto.PlayableDto(dto("7H"), true, java.util.List.of()),
                new NinetyNineDto.PlayableDto(dto("8H"), true, java.util.List.of()),
                new NinetyNineDto.PlayableDto(dto("2H"), true, java.util.List.of()));
        assertThat(view(game, S1).availableActions()).containsExactly("PLAY");
    }

    @Test
    void aStuckAceIsPlayableAsOne() throws Exception {
        NinetyNine game = table.start(2, "KS AH 9S 8H 7S 2H 5D");
        table.actions.play(game.getId(), S0, card("KS"), null);

        assertThat(view(game, S1).playable().get(0))
                .isEqualTo(new NinetyNineDto.PlayableDto(dto("AH"), true, java.util.List.of(1)));
    }

    @Test
    void aBustShowsTheRemainingHandToEverySeatAndTheLifeLost() throws Exception {
        NinetyNine game = table.beforeABust();

        table.actions.play(game.getId(), S0, card("KS"), null);
        table.actions.play(game.getId(), S1, card("7H"), null);

        for (PlayerId viewer : new PlayerId[] {S0, S1, S2}) {
            NinetyNineDto dto = view(game, viewer);
            assertThat(dto.phase()).isEqualTo("ROUND_OVER");
            assertThat(dto.stuck()).isFalse();
            assertThat(dto.total()).isEqualTo(106);
            assertThat(dto.lastPlay()).isEqualTo(new NinetyNineDto.LastPlayDto(1, dto("7H"), null, 99, 106, false));
            assertThat(dto.lastBust()).isEqualTo(new NinetyNineDto.LastBustDto(1, 106,
                    cards("8H 2H").stream().map(CardDto::from).toList(), 2, false));
            assertThat(dto.players().get(1).lives()).isEqualTo(2);
            assertThat(dto.availableActions()).isEmpty();
            assertThat(dto.playable()).isEmpty();
        }
    }

    @Test
    void theLastLifeLostEliminatesTheSeatAndFinishesTheGame() throws Exception {
        NinetyNine game = table.start(2, org.kevinkib.cardgames.game.GameOptions.of(Map.of("lives", "2")),
                "KS 7H 9S 8H 4S 2H 5D", "KD 9D 5D 8D 6D 7D 2D 3D");
        table.actions.play(game.getId(), S0, card("KS"), null); // seat 1 is stuck: first life lost
        table.actions.play(game.getId(), S1, card("7H"), null);
        table.actions.nextRound(game.getId(), game.version());
        table.actions.play(game.getId(), S1, card("9D"), null);

        table.actions.play(game.getId(), S0, card("KD"), null);
        table.actions.play(game.getId(), S1, card("8D"), null); // seat 1 is stuck again: out

        NinetyNineDto dto = view(game, S0);
        assertThat(dto.phase()).isEqualTo("FINISHED");
        assertThat(dto.outcome()).isEqualTo(new NinetyNineDto.OutcomeDto.Finished(java.util.List.of(0), "LAST_STANDING"));
        assertThat(dto.players().get(1)).satisfies(p -> {
            assertThat(p.eliminated()).isTrue();
            assertThat(p.lives()).isZero();
            assertThat(p.place()).isEqualTo(2);
            assertThat(p.handSize()).isZero();
        });
        assertThat(dto.players().get(0).place()).isEqualTo(1);
        assertThat(dto.lastBust().eliminated()).isTrue();
        assertThat(dto.lastBust().livesLeft()).isZero();
    }

    @Test
    void aForfeitFinishesTheGameAndNamesTheWinnerAndThePlaces() {
        NinetyNine game = table.start(2, "KS 10H 5C 9H AS 7H");

        game.forfeit(S1);

        NinetyNineDto dto = view(game, S0);
        assertThat(dto.phase()).isEqualTo("FINISHED");
        assertThat(dto.outcome()).isEqualTo(new NinetyNineDto.OutcomeDto.Finished(java.util.List.of(0), "FORFEIT"));
        assertThat(dto.players().get(1).eliminated()).isTrue();
        assertThat(dto.players().get(1).place()).isEqualTo(2);
        assertThat(dto.players().get(0).place()).isEqualTo(1);
    }

    @Test
    void aBotSeatIsFlaggedWithItsLevelAndNoName() {
        NinetyNine game = table.start(2, "KS 10H 5C 9H AS 7H");
        BotSeatOptions levels = (id, seat) -> seat.equals(S1) ? Map.of("level", "hard") : Map.of();

        NinetyNineDto dto = NinetyNineDto.forViewer(game, S0, Map.of(S0, "Alice"), Set.of(S1), levels);

        assertThat(dto.players().get(1).bot()).isTrue();
        assertThat(dto.players().get(1).level()).isEqualTo("hard");
        assertThat(dto.players().get(1).name()).isNull();
        assertThat(dto.players().get(0).bot()).isFalse();
        assertThat(dto.players().get(0).level()).isNull();
    }

    @Test
    void aBotWithoutALevelOptionShowsTheDefaultLevel() {
        NinetyNine game = table.start(2, "KS 10H 5C 9H AS 7H");

        NinetyNineDto dto = NinetyNineDto.forViewer(game, S0, Map.of(), Set.of(S1), BotSeatOptions.none());

        assertThat(dto.players().get(1).level()).isEqualTo("medium");
    }
}
