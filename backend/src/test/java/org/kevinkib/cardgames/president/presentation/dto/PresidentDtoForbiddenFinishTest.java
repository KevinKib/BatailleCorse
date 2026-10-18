package org.kevinkib.cardgames.president.presentation.dto;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.presentation.dto.event.PresidentEventData;
import org.kevinkib.cardgames.president.presentation.dto.event.PresidentEventType;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * What the wire says about the trick variants of PR V2 (additive): the options in force, the forbidden finishers on
 * the seats and in the round summary, and the reasons a trick was taken.
 */
class PresidentDtoForbiddenFinishTest {

    private static PlayerId p(int seat) {
        return new PlayerId(seat);
    }

    private static PresidentDto view(President game, int seat) {
        return PresidentDto.forViewer(game, p(seat), Map.of(), Set.of());
    }

    @Test
    void theOptionsInForceCarryTheTrickVariants() {
        President game = aPresident().withHand("4C 9C").withHand("5C JC").withHand("6C QC").leader(0)
                .options(PresidentOptions.defaults().withMustPlay(true)).build();

        Map<String, String> options = view(game, 0).options();

        assertThat(options.containsKey("twoCuts"), is(false));
        assertThat(options.containsKey("quadClears"), is(false));
        assertThat(options.keySet().containsAll(List.of("equalPlay", "firstLead", "forbiddenFinish")), is(false));
        assertThat(options.get("mustPlay"), is("on"));
    }

    @Test
    void aForbiddenFinisherIsOutWithoutAPlaceWhileTheRoundGoesOn() throws Exception {
        President game = aPresident().leader(0)
                .withHand("KS 3C").withHand("AS 4C").withHand("2S").withHand("3H 5C").build();
        game.play(p(0), cards("KS"));
        game.play(p(1), cards("AS"));
        game.play(p(2), cards("2S"));

        PresidentDto dto = view(game, 3);

        PresidentDto.PlayerDto c = dto.players().get(2);
        assertThat(c.out(), is(true));
        assertThat(c.forbiddenFinish(), is(true));
        assertThat(c.place(), is(nullValue()));
        assertThat(c.handSize(), is(0));
        assertThat(dto.players().get(0).forbiddenFinish(), is(false));
        assertThat(dto.players().get(0).out(), is(false));
    }

    @Test
    void theRoundSummaryPlacesTheForbiddenFinisherLastAndFlagsThem() throws Exception {
        President game = aPresident().leader(0)
                .withHand("2S").withHand("3C 4C").withHand("5C 6C").build();
        game.play(p(0), cards("2S"));   // a forbidden finish does not close the trick
        game.pass(p(1));
        game.pass(p(2));                // nobody holds a 2: the trick ends and B leads
        game.play(p(1), cards("3C"));
        game.pass(p(2));
        game.play(p(1), cards("4C"));

        PresidentDto over = view(game, 2);

        assertThat(over.phase(), is("ROUND_OVER"));
        assertThat(over.lastRound().finishingOrder(), contains(1, 2, 0));
        PresidentDto.PlayerRoundDto last = over.lastRound().players().get(2);
        assertThat(last.seat(), is(0));
        assertThat(last.place(), is(3));
        assertThat(last.title(), is("SCUM"));
        assertThat(last.forbiddenFinish(), is(true));
        assertThat(over.lastRound().players().get(0).forbiddenFinish(), is(false));
        assertThat(over.players().get(0).place(), is(3));
        assertThat(over.players().get(0).forbiddenFinish(), is(true));
        assertThat(over.players().get(1).forbiddenFinish(), is(false));
    }

    @Test
    void aForbiddenFinishNamesNoPlaceAndClosesNoTrick() throws Exception {
        President game = aPresident().leader(0)
                .withHand("9S 9H 3C").withHand("2S").withHand("KC KD").withHand("QC 6C").build();
        game.play(p(0), cards("9S"));

        List<PresidentEvent> events = game.play(p(1), cards("2S"));
        PresidentEventData data = PresidentEventData.from(PresidentEventType.PLAY, 1, events);

        assertThat(data.trickEndReason(), is(nullValue()));
        assertThat(data.trickWinner(), is(nullValue()));
        assertThat(data.outPlace(), is(nullValue()));
        assertThat(data.outForbidden(), is(true));
    }

    @Test
    void anOrdinaryGoingOutKeepsItsPlaceAndIsNotForbidden() throws Exception {
        President game = aPresident().leader(0)
                .withHand("4C").withHand("5C 6C").withHand("7C 8C 9C").build();

        List<PresidentEvent> events = game.play(p(0), cards("4C"));
        PresidentEventData data = PresidentEventData.from(PresidentEventType.PLAY, 0, events);

        assertThat(data.outPlace(), is(1));
        assertThat(data.outForbidden(), is(false));
    }
}
