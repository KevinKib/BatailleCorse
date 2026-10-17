package org.kevinkib.cardgames.president.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.round.CompletionClosedException;
import org.kevinkib.cardgames.president.presentation.api.PresidentCardsPayload;
import org.kevinkib.cardgames.president.presentation.dto.PresidentDto;
import org.kevinkib.cardgames.president.presentation.dto.event.PresidentEventData;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.card;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * The completion of the four at the table: the holder alone sees the cards that complete it, the chain and the
 * binding are public, the same play destination serves both a play and a completion, an out-of-turn completion is
 * announced as such, and a late one is refused to its sender alone.
 */
class PresidentCompletionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PresidentTable table = new PresidentTable();

    private static PlayerId seat(int id) {
        return new PlayerId(id);
    }

    private static PresidentDto view(President game, int seat) {
        return PresidentDto.forViewer(game, seat(seat), Map.of(), Set.of());
    }

    private static List<CardDto> dtos(String... codes) {
        return java.util.Arrays.stream(codes).map(c -> CardDto.from(card(c))).toList();
    }

    /** A 9S, B 9H played; C holds the two missing 9s; D holds a 10. C is to act, bound to a 9. */
    private President windowOpenForC() throws Exception {
        President game = table.install(aPresident().leader(0)
                .withHand("9S 3C").withHand("9H 4C").withHand("9D 9C 5C").withHand("10H 6C").build());
        table.actions.play(game.getId(), seat(0), cards("9S"));
        table.actions.play(game.getId(), seat(1), cards("9H"));
        table.messaging.clear();
        return game;
    }

    private PresidentCardsPayload payload(President game, int seat, List<CardDto> cards) {
        return new PresidentCardsPayload(game.getId().uuid().toString(), table.token(game, seat), cards);
    }

    // ---- the DTO

    @Test
    void theChainAndTheBindingArePublicToEverySeat() throws Exception {
        President game = windowOpenForC();

        for (int s = 0; s < 4; s++) {
            PresidentDto.TrickDto trick = view(game, s).trick();
            assertThat(trick.constraint(), is("MATCH_OR_PASS"));
            assertThat(trick.requiredRank(), is("NINE"));
            assertThat(trick.requiredCount(), is(1));
            assertThat(trick.chain().rank(), is("NINE"));
            assertThat(trick.chain().plays(), is(2));
            assertThat(trick.chain().cards(), containsInAnyOrder(dtos("9S", "9H").toArray()));
        }
    }

    @Test
    void anEmptyTrickIsFreeAndHasNoChain() {
        President game = aPresident().leader(0).withHand("4C 9C").withHand("5C").withHand("6C").build();

        PresidentDto.TrickDto trick = view(game, 0).trick();

        assertThat(trick.constraint(), is("FREE"));
        assertThat(trick.chain(), is(nullValue()));
        assertThat(trick.requiredRank(), is(nullValue()));
        assertThat(trick.requiredCount(), is(nullValue()));
    }

    @Test
    void afterAPassTheBindingIsGoneButTheChainStays() throws Exception {
        President game = windowOpenForC();
        table.actions.pass(game.getId(), seat(2));

        PresidentDto.TrickDto trick = view(game, 3).trick();

        assertThat(trick.constraint(), is("BEAT"));
        assertThat(trick.requiredRank(), is(nullValue()));
        assertThat(trick.chain().plays(), is(2));
    }

    @Test
    void theCompletionIsInTheHoldersDtoOnlyAndLeaksToNobodyElse() throws Exception {
        President game = windowOpenForC();

        assertThat(view(game, 2).completion(), containsInAnyOrder(dtos("9D", "9C").toArray()));
        assertThat(view(game, 2).availableActions(), hasItem("COMPLETE"));
        for (int s : new int[] {0, 1, 3}) {
            PresidentDto other = view(game, s);
            assertThat(other.completion(), is(nullValue()));
            assertThat(other.availableActions(), not(hasItem("COMPLETE")));
            String json = MAPPER.writeValueAsString(other);
            assertThat(json.contains("completion"), is(false));
            assertThat(json.contains(CardDto.from(card("9D")).name()), is(false));
            assertThat(json.contains(CardDto.from(card("9C")).name()), is(false));
        }
    }

    @Test
    void theCompletionSurvivesEveryPassAndShowsOutOfTurn() throws Exception {
        President game = windowOpenForC();

        table.actions.pass(game.getId(), seat(2));
        assertThat(view(game, 2).currentSeat(), is(3));
        assertThat(view(game, 2).completion(), containsInAnyOrder(dtos("9D", "9C").toArray()));
        assertThat(view(game, 2).availableActions(), is(List.of("COMPLETE")));
        table.actions.pass(game.getId(), seat(3));
        assertThat(view(game, 2).completion(), containsInAnyOrder(dtos("9D", "9C").toArray()));
    }

    @Test
    void theCompletionGoesWithTheNextPlay() throws Exception {
        President game = windowOpenForC();
        table.actions.pass(game.getId(), seat(2));

        table.actions.play(game.getId(), seat(3), cards("10H"));

        assertThat(view(game, 2).completion(), is(nullValue()));
        assertThat(view(game, 2).availableActions(), is(List.of()));
    }

    @Test
    void theCompletionGoesWhenTheTrickCloses() throws Exception {
        President game = windowOpenForC();
        table.actions.play(game.getId(), seat(2), cards("9D 9C"));

        assertThat(view(game, 2).completion(), is(nullValue()));
        assertThat(view(game, 2).trick().chain(), is(nullValue()));
        assertThat(view(game, 2).currentSeat(), is(2));
    }

    // ---- the actions and the controller

    @Test
    void anOutOfTurnCompletionIsBroadcastAsCompletionToEverySeat() throws Exception {
        President game = windowOpenForC();
        table.actions.pass(game.getId(), seat(2));
        table.messaging.clear();

        table.actions.play(game.getId(), seat(2), cards("9D 9C"));

        assertThat(table.messaging.seats.size(), is(4));
        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType(), is("COMPLETION"));
        PresidentEventData data = (PresidentEventData) first.getEventData();
        assertThat(data.seat(), is(2));
        assertThat(data.action(), is("COMPLETION"));
        assertThat(data.cards(), containsInAnyOrder(dtos("9D", "9C").toArray()));
        assertThat(data.trickWinner(), is(2));
        assertThat(data.trickEndReason(), is("QUAD"));
        assertThat(((PresidentDto) first.getState()).currentSeat(), is(2));
    }

    @Test
    void aQuadPlayedAtOnceIsAnOrdinaryPlayEventThatClosesTheTrickWithQuad() throws Exception {
        President game = table.install(aPresident().leader(0)
                .withHand("5S 5H 5D 5C 3C").withHand("9H 4C").withHand("10H 6C").build());
        table.messaging.clear();

        table.actions.play(game.getId(), seat(0), cards("5S 5H 5D 5C"));

        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType(), is("PLAY"));
        PresidentEventData data = (PresidentEventData) first.getEventData();
        assertThat(data.trickEndReason(), is("QUAD"));
        assertThat(data.trickWinner(), is(0));
        assertThat(((PresidentDto) first.getState()).currentSeat(), is(0));
        assertThat(((PresidentDto) first.getState()).trick().plays().isEmpty(), is(true));
    }

    @Test
    void aCompletionInTurnIsAnOrdinaryPlayThatClosesTheTrick() throws Exception {
        President game = windowOpenForC();

        table.actions.play(game.getId(), seat(2), cards("9D 9C"));

        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType(), is("PLAY"));
        assertThat(((PresidentEventData) first.getEventData()).trickEndReason(), is("QUAD"));
    }

    @Test
    void theWebSocketPlayDestinationServesTheCompletionOutOfTurn() throws Exception {
        President game = windowOpenForC();
        table.actions.pass(game.getId(), seat(2));
        table.messaging.clear();

        table.ws.play(payload(game, 2, dtos("9D", "9C")));

        assertThat(table.messaging.payloads.size(), is(4));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("COMPLETION"));
        assertThat(game.currentSeat(), is(seat(2)));
    }

    @Test
    void aLateCompletionIsRefusedToItsSenderAloneAndNothingChanges() throws Exception {
        President game = windowOpenForC();
        table.actions.pass(game.getId(), seat(2));
        table.actions.play(game.getId(), seat(3), cards("10H"));
        table.messaging.clear();
        int version = game.version();

        table.ws.play(payload(game, 2, dtos("9D", "9C")));

        assertThat(table.messaging.seats, is(List.of(seat(2))));
        Response answer = table.messaging.payloads.get(0);
        assertThat(answer.isSuccess(), is(false));
        assertThat(game.version(), is(version));
        assertThrows(CompletionClosedException.class,
                () -> table.actions.play(game.getId(), seat(2), cards("9D 9C")));
    }
}
