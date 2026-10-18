package org.kevinkib.cardgames.president.presentation.dto;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.RandomDeckSource;
import org.kevinkib.cardgames.president.domain.rules.MatchLength;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.card;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.codes;

/** What each seat sees of a President game: its own hand and exchange, everyone's public state, nothing else. */
class PresidentDtoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static PlayerId p(int seat) {
        return new PlayerId(seat);
    }

    private static PresidentDto view(President game, int seat) {
        return PresidentDto.forViewer(game, p(seat), Map.of(), Set.of());
    }

    private static String json(PresidentDto dto) {
        return MAPPER.writeValueAsString(dto);
    }

    private static boolean mentions(String json, String code) {
        return json.contains("\"name\":\"" + CardDto.from(card(code)).name() + "\"");
    }

    private static List<String> handCodes(PresidentDto dto) {
        return dto.hand().stream().map(c -> c.name()).toList();
    }

    private static List<String> names(String... codes) {
        return java.util.Arrays.stream(codes).map(c -> CardDto.from(card(c)).name()).toList();
    }

    /** A 4-player round 1 played with the lowest plays until the round is over; then everyone is ready. */
    private static President roundTwo(long seed) throws Exception {
        President game = new President(GameId.generate(), 4, seed, new RandomDeckSource());
        org.kevinkib.cardgames.president.presentation.PresidentRounds.playRoundOut(game);
        org.kevinkib.cardgames.president.presentation.PresidentRounds.readyAll(game);
        return game;
    }

    // ---- shape

    @Test
    void shapeOfARoundOneState() {
        President game = aPresident().withHand("4C 9C 9D").withHand("5C JC").withHand("6C QC").leader(0).build();

        PresidentDto dto = PresidentDto.forViewer(game, p(0), Map.of(p(1), "Bob"), Set.of(p(2)));

        assertThat(dto.started(), is(true));
        assertThat(dto.id(), is(game.getId().uuid().toString()));
        assertThat(dto.gameType(), is("president"));
        assertThat(dto.version(), is(0));
        assertThat(dto.round(), is(1));
        assertThat(dto.rounds(), is(5));
        assertThat(dto.phase(), is("PLAYING"));
        assertThat(dto.currentSeat(), is(0));
        assertThat(dto.options(), is(Map.ofEntries(
                Map.entry("afterPass", "reenter"), Map.entry("exchange", "two"),
                Map.entry("giveBack", "free"), Map.entry("nextLead", "scum"), Map.entry("scoring", "rank"),
                Map.entry("length", "rounds-5"), Map.entry("mustPlay", "off"), Map.entry("reducedCut", "off"))));
        assertThat(dto.players().size(), is(3));
        PresidentDto.PlayerDto bob = dto.players().get(1);
        assertThat(bob.seat(), is(1));
        assertThat(bob.name(), is("Bob"));
        assertThat(bob.handSize(), is(2));
        assertThat(bob.bot(), is(false));
        assertThat(bob.title(), is(nullValue()));
        assertThat(bob.place(), is(nullValue()));
        assertThat(bob.out(), is(false));
        assertThat(bob.points(), is(0));
        assertThat(dto.players().get(2).bot(), is(true));
        assertThat(dto.trick().leader(), is(0));
        assertThat(dto.trick().plays(), is(empty()));
        assertThat(dto.trick().top(), is(nullValue()));
        assertThat(dto.exchange(), is(empty()));
        assertThat(dto.lastRound(), is(nullValue()));
        assertThat(dto.outcome(), instanceOf(PresidentDto.OutcomeDto.Ongoing.class));
        assertThat(dto.toGiveBack(), is(0));
        assertThat(dto.received(), is(empty()));
        assertThat(dto.given(), is(empty()));
    }

    @Test
    void theViewerSeesItsOwnHandSortedFromTheWeakestAndItsLegalPlaysAsPatterns() {
        President game = aPresident().withHand("9D 4C 9C").withHand("5C JC").withHand("6C QC").leader(0).build();

        PresidentDto dto = view(game, 0);

        assertThat(handCodes(dto).size(), is(3));
        assertThat(dto.hand().get(0).name(), is(CardDto.from(card("4C")).name()));
        assertThat(dto.legalPlays(), contains(
                new PresidentDto.PatternDto("SET", "FOUR", 1),
                new PresidentDto.PatternDto("SET", "NINE", 1),
                new PresidentDto.PatternDto("SET", "NINE", 2)));
        assertThat(dto.availableActions(), contains("PLAY"));
    }

    @Test
    void roundsIsMissingForEveryLengthButARoundsTarget() {
        President endless = new President(GameId.generate(), 3, 1L, new RandomDeckSource(),
                PresidentOptions.defaults().withLength(MatchLength.ENDLESS));
        President points = new President(GameId.generate(), 3, 1L, new RandomDeckSource(),
                PresidentOptions.defaults().withLength(MatchLength.POINTS_21));
        President three = new President(GameId.generate(), 3, 1L, new RandomDeckSource(),
                PresidentOptions.defaults().withLength(MatchLength.ROUNDS_3));

        assertThat(view(endless, 0).rounds(), is(nullValue()));
        assertThat(view(endless, 0).options().get("length"), is("endless"));
        assertThat(view(points, 0).rounds(), is(nullValue()));
        assertThat(view(three, 0).rounds(), is(3));
    }

    // ---- hands stay private

    @Test
    void noSeatSeesAnotherSeatsHandInAnyState() throws Exception {
        President game = aPresident().withHand("4C 9C 9D").withHand("5C JC").withHand("6C QC").leader(0).build();
        List<String> all = List.of("4C", "9C", "9D", "5C", "JC", "6C", "QC");
        List<List<String>> hands = List.of(List.of("4C", "9C", "9D"), List.of("5C", "JC"), List.of("6C", "QC"));

        for (int viewer = 0; viewer < 3; viewer++) {
            String text = json(view(game, viewer));
            for (int seat = 0; seat < 3; seat++) {
                for (String code : hands.get(seat)) {
                    assertThat("viewer " + viewer + " sees " + code, mentions(text, code), is(seat == viewer));
                }
            }
        }
        assertThat(all.size(), is(7));
    }

    @Test
    void playedCardsArePublicToEverySeatButTheRestOfTheHandIsNot() throws Exception {
        President game = aPresident().withHand("4C 9C 9D").withHand("5C JC").withHand("6C QC").leader(0).build();
        game.play(p(0), cards("9C 9D"));

        for (int viewer = 1; viewer < 3; viewer++) {
            PresidentDto dto = view(game, viewer);
            String text = json(dto);
            assertThat(mentions(text, "9C") && mentions(text, "9D"), is(true));
            assertThat(mentions(text, "4C"), is(false));
            assertThat(dto.trick().plays().size(), is(1));
            assertThat(dto.trick().plays().get(0).seat(), is(0));
            assertThat(dto.trick().top(), is(new PresidentDto.PatternDto("SET", "NINE", 2)));
            assertThat(dto.trick().lastPlayer(), is(0));
        }
    }

    // ---- legal plays and actions only for the seat to act

    @Test
    void legalPlaysAndActionsAreOnlyForTheSeatToAct() throws Exception {
        President game = aPresident().withHand("4C 9C 9D").withHand("5C JC").withHand("6C QC").leader(0).build();
        game.play(p(0), cards("9C 9D"));

        PresidentDto other = view(game, 2);
        assertThat(other.legalPlays(), is(empty()));
        assertThat(other.availableActions(), is(empty()));

        PresidentDto current = view(game, 1);
        assertThat(current.currentSeat(), is(1));
        assertThat(current.legalPlays(), is(empty()));
        assertThat(current.availableActions(), contains("PASS"));
    }

    @Test
    void aPassShowsOnTheSeatAndOnTheTrickUntilTheNextPlay() throws Exception {
        President game = aPresident().withHand("4C 9C 9D").withHand("5C JC").withHand("6C QC").leader(0).build();
        game.play(p(0), cards("4C"));
        game.pass(p(1));

        PresidentDto dto = view(game, 2);
        assertThat(dto.trick().passed(), contains(1));
        assertThat(dto.players().get(1).passed(), is(true));
        assertThat(dto.players().get(2).passed(), is(false));

        game.play(p(2), cards("6C"));
        assertThat(view(game, 0).trick().passed(), is(empty()));
        assertThat(view(game, 0).players().get(1).passed(), is(false));
    }

    // ---- going out, the end of a round

    @Test
    void aSeatThatWentOutShowsItsPlaceAndTheRoundSummaryComesWithTheRoundEnd() throws Exception {
        President game = aPresident().withHand("4C").withHand("5C 6C").withHand("7C 8C 9C").leader(0).build();

        game.play(p(0), cards("4C"));
        PresidentDto afterFirstOut = view(game, 1);
        assertThat(afterFirstOut.players().get(0).out(), is(true));
        assertThat(afterFirstOut.players().get(0).place(), is(1));
        assertThat(afterFirstOut.players().get(0).handSize(), is(0));
        assertThat(afterFirstOut.trick().plays(), is(empty()));

        game.play(p(1), cards("5C"));
        game.pass(p(2));
        game.play(p(1), cards("6C"));

        PresidentDto over = view(game, 2);
        assertThat(over.phase(), is("ROUND_OVER"));
        assertThat(over.availableActions(), contains("READY"));
        assertThat(over.lastRound().round(), is(1));
        assertThat(over.lastRound().finishingOrder(), contains(0, 1, 2));
        assertThat(over.lastRound().players().get(0).title(), is("PRESIDENT"));
        assertThat(over.lastRound().players().get(1).title(), is("NEUTRAL"));
        assertThat(over.lastRound().players().get(2).title(), is("SCUM"));
        assertThat(over.lastRound().players().get(0).points(), is(2));
        assertThat(over.lastRound().players().get(2).total(), is(0));
        assertThat(over.standings().get(0), is(new PresidentDto.StandingDto(0, 2)));
        assertThat(over.players().get(0).points(), is(2));
        assertThat(over.players().get(2).place(), is(3));
    }

    @Test
    void readySeatsAreShownAtTheBarrierAndTheReadySeatHasNothingLeftToDo() throws Exception {
        President game = aPresident().withHand("4C").withHand("5C 6C").withHand("7C 8C 9C").leader(0).build();
        game.play(p(0), cards("4C"));
        game.play(p(1), cards("5C"));
        game.pass(p(2));
        game.play(p(1), cards("6C"));

        game.ready(p(2));

        PresidentDto dto = view(game, 2);
        assertThat(dto.players().get(2).ready(), is(true));
        assertThat(dto.players().get(0).ready(), is(false));
        assertThat(dto.availableActions(), is(empty()));
        assertThat(view(game, 0).availableActions(), contains("READY"));
    }

    // ---- the exchange

    @Test
    void theExchangeIsVisibleOnlyToItsPairAndEveryoneSeesTheCounts() throws Exception {
        President game = roundTwo(5L);
        assertThat(game.phase().name(), is("EXCHANGE"));
        List<org.kevinkib.cardgames.president.domain.exchange.PendingReturn> pairs = game.exchange();
        PlayerId president = pairs.get(0).high();
        PlayerId scum = pairs.get(0).low();
        PlayerId vicePresident = pairs.get(1).high();
        PlayerId viceScum = pairs.get(1).low();

        PresidentDto high = view(game, president.id());
        assertThat(high.toGiveBack(), is(2));
        assertThat(high.availableActions(), contains("GIVE_BACK"));
        assertThat(high.received().stream().map(CardDto::name).toList(),
                is(game.received(president).stream().map(c -> CardDto.from(c).name()).toList()));
        assertThat(high.received().size(), is(2));
        assertThat(high.given(), is(empty()));

        PresidentDto low = view(game, scum.id());
        assertThat(low.toGiveBack(), is(0));
        assertThat(low.given().size(), is(2));
        assertThat(low.availableActions(), is(empty()));
        assertThat(low.received(), is(empty()));

        // the other pair sees none of those cards, only the counts of the pairs
        String bystander = json(view(game, vicePresident.id()));
        for (org.kevinkib.cards.domain.Card moved : game.received(president)) {
            assertThat(bystander, not(containsString("\"name\":\"" + CardDto.from(moved).name() + "\"")));
        }
        PresidentDto other = view(game, viceScum.id());
        assertThat(other.received(), is(empty()));
        assertThat(other.given().size(), is(1));
        for (PresidentDto dto : List.of(high, low, other, view(game, vicePresident.id()))) {
            assertThat(dto.exchange(), contains(
                    new PresidentDto.PairDto(president.id(), scum.id(), 2, false),
                    new PresidentDto.PairDto(vicePresident.id(), viceScum.id(), 1, false)));
        }
    }

    @Test
    void afterTheReturnTheLowTitleSeesWhatItGotBackAndTheHighTitleWhatItGave() throws Exception {
        President game = roundTwo(5L);
        PlayerId president = game.exchange().get(0).high();
        PlayerId scum = game.exchange().get(0).low();
        List<org.kevinkib.cards.domain.Card> back = new ArrayList<>(game.hand(president).subList(0, 2));

        game.giveBack(president, back);

        PresidentDto high = view(game, president.id());
        assertThat(high.toGiveBack(), is(0));
        assertThat(high.given().stream().map(CardDto::name).toList(),
                is(back.stream().map(c -> CardDto.from(c).name()).toList()));
        PresidentDto low = view(game, scum.id());
        assertThat(low.received().stream().map(CardDto::name).toList(),
                is(back.stream().map(c -> CardDto.from(c).name()).toList()));
        assertThat(view(game, president.id()).exchange().get(0).done(), is(true));
    }

    @Test
    void titlesInForceShowOnTheSeatsFromRoundTwo() throws Exception {
        President game = roundTwo(5L);

        PresidentDto dto = view(game, 0);

        List<String> titles = dto.players().stream().map(PresidentDto.PlayerDto::title).toList();
        assertThat(titles.stream().filter("PRESIDENT"::equals).count(), is(1L));
        assertThat(titles.stream().filter("VICE_PRESIDENT"::equals).count(), is(1L));
        assertThat(titles.stream().filter("VICE_SCUM"::equals).count(), is(1L));
        assertThat(titles.stream().filter("SCUM"::equals).count(), is(1L));
        assertThat(dto.round(), is(2));
    }

    // ---- the end of the match

    @Test
    void aMatchLeftWithOnePlayerIsFinishedWithTheWinnerAndTheForfeitReason() {
        President game = aPresident().withHand("4C").withHand("5C 6C").withHand("7C 8C 9C").leader(0).build();
        game.forfeit(p(1));
        game.forfeit(p(2));

        PresidentDto dto = view(game, 0);

        assertThat(dto.phase(), is("FINISHED"));
        assertThat(dto.availableActions(), is(empty()));
        assertThat(dto.outcome(), is(new PresidentDto.OutcomeDto.Finished(List.of(0), "FORFEIT")));
        assertThat(json(dto), containsString("\"status\":\"FINISHED\""));
    }

    @Test
    void theJsonCarriesTheGameType() {
        President game = aPresident().withHand("4C").withHand("5C").withHand("6C").leader(0).build();

        assertThat(json(view(game, 0)), containsString("\"gameType\":\"president\""));
        assertThat(codes(game.hand(p(0))), is("4C"));
        assertThat(names("4C").size(), is(1));
    }
}
