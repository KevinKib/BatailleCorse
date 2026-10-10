package org.kevinkib.cardgames.kobo.presentation.dto;

import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cards.domain.Card;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.aKobo;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.ref;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;
import static org.kevinkib.cardgames.kobo.domain.KoboFixtures.card;

class KoboDtoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String json(KoboDto dto) {
        return MAPPER.writeValueAsString(dto);
    }

    private static String name(String code) {
        return CardDto.from(card(code)).name();
    }

    private static boolean mentions(String json, String code) {
        return json.contains("\"name\":\"" + name(code) + "\"");
    }

    /** Distinct card on every hidden position, so that a leak is detectable by name. */
    private Kobo midRound(Phase phase) {
        return aKobo()
                .withTableau("AH", "2H", "3H", "4H")
                .withTableau("5S", "6S", "9D", "KS")
                .withTableau("2C", "3C", "4C", "KC")
                .withDiscard("QD").withDrawPile("8C", "9C", "10C", "JC")
                .inPhase(phase).build();
    }

    @Test
    void shapeOfAMidRoundState() {
        Kobo kobo = midRound(Phase.DRAW);

        KoboDto dto = KoboDto.forViewer(kobo, seat(0), Map.of(seat(1), "Bob"));

        assertThat(dto.started(), is(true));
        assertThat(dto.gameType(), is("kobo"));
        assertThat(dto.phase(), is("DRAW"));
        assertThat(dto.currentSeat(), is(0));
        assertThat(dto.round(), is(1));
        assertThat(dto.drawPileSize(), is(4));
        assertThat(dto.discardSize(), is(1));
        assertThat(dto.discardTop().name(), is(name("QD")));
        assertThat(dto.players(), hasSize(3));
        assertThat(dto.players().get(1).name(), is("Bob"));
        assertThat(dto.players().get(0).current(), is(true));
        assertThat(dto.players().get(0).slots(), hasSize(4));
        assertThat(dto.players().get(0).handSize(), is(4));
        assertThat(dto.availableActions(), contains("DRAW", "MATCH_DISCARD"));
        assertThat(dto.outcome(), instanceOf(OutcomeDto.Ongoing.class));
        assertThat(dto.lastRound(), nullValue());
    }

    @Test
    void faceDownCardsAreNeverInAnyViewersJson() {
        Kobo kobo = midRound(Phase.DRAW);

        for (int viewer = 0; viewer < 3; viewer++) {
            String json = json(KoboDto.forViewer(kobo, seat(viewer), Map.of()));
            for (String hidden : List.of("AH", "2H", "3H", "4H", "5S", "6S", "9D", "KS", "2C", "3C", "4C", "KC",
                    "8C", "9C", "10C", "JC")) {
                assertThat("viewer " + viewer + " leaks " + hidden, mentions(json, hidden), is(false));
            }
            assertThat(mentions(json, "QD"), is(true)); // the public discard top
        }
    }

    @Test
    void startingCardsAreThePrivateTopRowOfTheViewerOnly() {
        Kobo kobo = new Kobo(GameId.generate(), 3, 3L);
        PlayerId p0 = seat(0);

        KoboDto mine = KoboDto.forViewer(kobo, p0);
        KoboDto theirs = KoboDto.forViewer(kobo, seat(1));

        assertThat(mine.startingCards(), hasSize(2));
        assertThat(mine.startingCards().get(0).slot(), is(2));
        assertThat(mine.startingCards().get(0).card().name(),
                is(CardDto.from(kobo.slots(p0).get(2).card()).name()));
        String hiddenOfP0 = CardDto.from(kobo.slots(p0).get(2).card()).name();
        assertThat(json(theirs), not(containsString("\"name\":\"" + hiddenOfP0 + "\"")));
        assertThat(mine.availableActions(), contains("READY"));
    }

    @Test
    void drawnCardIsVisibleOnlyToTheCurrentPlayerWhileDeciding() {
        Kobo kobo = aKobo().withPlayers(3).inPhase(Phase.DECISION).withDrawn("9S").build();

        KoboDto current = KoboDto.forViewer(kobo, seat(0));
        KoboDto other = KoboDto.forViewer(kobo, seat(1));

        assertThat(current.drawnCard().name(), is(name("9S")));
        assertThat(current.hasDrawn(), is(true));
        assertThat(other.drawnCard(), nullValue());
        assertThat(other.hasDrawn(), is(true));
        assertThat(mentions(json(other), "9S"), is(false));
    }

    @Test
    void aPeekIsVisibleOnlyToThePeekerAndGoesAwayAfterTheirNextCommand() throws Exception {
        Kobo kobo = aKobo().withTableau("AH", "2H", "3H", "4H").withTableau("5S", "6S", "9D", "KS")
                .inPhase(Phase.DECISION).withDrawn("9H").build();
        kobo.discardDrawn(seat(0));
        kobo.peek(seat(0), ref(1, 3));

        KoboDto peeker = KoboDto.forViewer(kobo, seat(0));
        KoboDto other = KoboDto.forViewer(kobo, seat(1));

        assertThat(peeker.reveal(), notNullValue());
        assertThat(peeker.reveal().card().name(), is(name("KS")));
        assertThat(peeker.reveal().seat(), is(1));
        assertThat(peeker.reveal().slot(), is(3));
        assertThat(other.reveal(), nullValue());
        assertThat(mentions(json(other), "KS"), is(false));

        kobo.endTurn(seat(0));
        assertThat(KoboDto.forViewer(kobo, seat(0)).reveal(), nullValue());
    }

    @Test
    void slotsExposeOccupancyAndRevisionButNoCard() throws Exception {
        Kobo kobo = midRound(Phase.DRAW);
        kobo.matchDiscard(seat(0), ref(0, 0), null, card("QD").getRank()); // wrong rank: penalty slot

        KoboDto dto = KoboDto.forViewer(kobo, seat(1));

        assertThat(dto.players().get(0).slots(), hasSize(5));
        assertThat(dto.players().get(0).slots().get(4).revision(), is(0));
        assertThat(dto.players().get(0).slots().get(0).card(), nullValue());
    }

    @Test
    void afterTheRoundIsRevealedEveryCardIsPublic() throws Exception {
        Kobo kobo = midRound(Phase.TURN_END);
        kobo.announceKobo(seat(0));
        for (int s : new int[]{1, 2}) {
            kobo.draw(seat(s));
            kobo.discardDrawn(seat(s));
            if (kobo.phase() == Phase.POWER) {
                kobo.skipPower(seat(s));
            }
            kobo.endTurn(seat(s));
        }
        assertThat(kobo.phase(), is(Phase.GIFT));

        KoboDto dto = KoboDto.forViewer(kobo, seat(2));

        assertThat(dto.revealed(), is(true));
        for (String shown : List.of("AH", "2H", "5S", "KS", "2C", "KC")) {
            assertThat(shown, mentions(json(dto), shown), is(true));
        }
        assertThat(dto.availableActions().contains("GIVE_TEN"), is(false));
        assertThat(KoboDto.forViewer(kobo, seat(0)).availableActions(), contains("GIVE_TEN"));

        kobo.giveTen(seat(0), seat(1));
        KoboDto over = KoboDto.forViewer(kobo, seat(1));
        assertThat(over.lastRound(), notNullValue());
        assertThat(over.lastRound().giftTo(), is(1));
        assertThat(over.lastRound().announcerWon(), is(true));
        assertThat(over.lastRound().players(), hasSize(3));
        assertThat(over.phase(), is("ROUND_OVER"));
    }

    @Test
    void aForfeitedViewerGetsNoPrivatePartAndNoActions() {
        Kobo kobo = aKobo().withPlayers(3).inPhase(Phase.DRAW).build();
        kobo.forfeit(seat(2));

        KoboDto gone = KoboDto.forViewer(kobo, seat(2));

        assertThat(gone.availableActions().isEmpty(), is(true));
        assertThat(gone.startingCards().isEmpty(), is(true));
        assertThat(gone.players(), hasSize(2));
    }

    @Test
    void finishedOutcomeNamesTheWinners() {
        Kobo kobo = aKobo().withPlayers(2).build();
        kobo.forfeit(seat(0));

        OutcomeDto outcome = KoboDto.forViewer(kobo, seat(1)).outcome();

        assertThat(outcome, instanceOf(OutcomeDto.Finished.class));
        assertThat(((OutcomeDto.Finished) outcome).winners(), contains(1));
        assertThat(((OutcomeDto.Finished) outcome).reason(), is("FORFEIT"));
        assertThat(json(KoboDto.forViewer(kobo, seat(1))), containsString("\"status\":\"FINISHED\""));
    }

    @Test
    void cardsInDtoHelperHandlesNull() {
        Card none = null;
        assertThat(CardDto.from(none), nullValue());
    }
}
