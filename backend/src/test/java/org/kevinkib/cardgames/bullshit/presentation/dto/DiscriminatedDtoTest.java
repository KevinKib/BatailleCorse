package org.kevinkib.cardgames.bullshit.presentation.dto;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.kevinkib.cardgames.bullshit.domain.BullshitBuilder.aBullshit;
import static org.kevinkib.cardgames.bullshit.domain.BullshitFixtures.playerWithRanks;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.ACE;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.KING;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.TWO;

class DiscriminatedDtoTest {

    @Test
    void givenOngoingGame_whenOutcomeFrom_thenOngoing() {
        Bullshit game = aBullshit().withPlayers(playerWithRanks(0, ACE), playerWithRanks(1, TWO)).build();

        assertThat(OutcomeDto.from(game), instanceOf(OutcomeDto.Ongoing.class));
    }

    @Test
    void givenFinishedGame_whenOutcomeFrom_thenWonWithWinnerId() {
        Bullshit game = aBullshit().withPlayers(playerWithRanks(0, ACE), playerWithRanks(1, TWO)).build();
        game.forfeit(new PlayerId(1)); // player 0 left standing

        OutcomeDto outcome = OutcomeDto.from(game);

        assertThat(outcome, instanceOf(OutcomeDto.Won.class));
        assertThat(((OutcomeDto.Won) outcome).winnerId(), is("0"));
    }

    @Test
    void givenNoDiscard_whenTableFrom_thenNoClaim() {
        Bullshit game = aBullshit().withPlayers(playerWithRanks(0, ACE), playerWithRanks(1, TWO)).build();

        assertThat(TableDto.from(game), instanceOf(TableDto.NoClaim.class));
    }

    @Test
    void givenDiscard_whenTableFrom_thenClaimWithCountAndNoCardIdentities() throws Exception {
        Bullshit game = aBullshit()
                .withPlayers(playerWithRanks(0, ACE, KING), playerWithRanks(1, TWO))
                .withCurrentTarget(ACE)
                .build();
        Card toPlay = game.getPlayers().get(0).getCards().get(0);
        game.discard(new PlayerId(0), List.of(toPlay));

        TableDto table = TableDto.from(game);

        assertThat(table, instanceOf(TableDto.Claim.class));
        TableDto.Claim claim = (TableDto.Claim) table;
        assertThat(claim.count(), is(1));
        String json = new ObjectMapper().writeValueAsString(claim);
        assertThat(json, containsString("\"count\":1"));
        assertThat(json, not(containsString("actualCards")));
    }

    @Test
    void givenPendingWinner_whenPendingFrom_thenPending() throws Exception {
        Bullshit game = aBullshit()
                .withPlayers(playerWithRanks(0, ACE), playerWithRanks(1, TWO))
                .withCurrentTarget(ACE)
                .build();
        Card onlyCard = game.getPlayers().get(0).getCards().get(0);
        game.discard(new PlayerId(0), List.of(onlyCard)); // player 0 empties hand -> pending winner

        PendingWinnerDto pending = PendingWinnerDto.from(game);

        assertThat(pending, instanceOf(PendingWinnerDto.Pending.class));
        assertThat(((PendingWinnerDto.Pending) pending).playerId(), is("0"));
    }

    @Test
    void givenOngoing_whenOutcomeSerialized_thenStatusOngoingNoWinnerField() throws Exception {
        String json = new ObjectMapper().writeValueAsString(new OutcomeDto.Ongoing());

        assertThat(json, containsString("\"status\":\"ONGOING\""));
        assertThat(json, not(containsString("winnerId")));
    }
}
