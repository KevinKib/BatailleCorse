package org.kevinkib.cardgames.bullshit.presentation.dto;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.game.PlayerId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.bullshit.domain.BullshitBuilder.aBullshit;
import static org.kevinkib.cardgames.bullshit.domain.BullshitFixtures.playerWithRanks;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.ACE;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.KING;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.TWO;

class BullshitDtoTest {

    @Test
    void givenViewer_whenForViewer_thenMyHandIsOwnCardsOnly() {
        Bullshit game = aBullshit()
                .withPlayers(playerWithRanks(0, ACE, KING), playerWithRanks(1, TWO))
                .build();

        BullshitDto dto = BullshitDto.forViewer(game, new PlayerId(0));

        assertThat(dto.myHand().size(), is(2));
        assertThat(dto.gameType(), is("bullshit"));
    }

    @Test
    void givenViewer_whenForViewer_thenOpponentsExposeOnlyCounts() {
        Bullshit game = aBullshit()
                .withPlayers(playerWithRanks(0, ACE), playerWithRanks(1, TWO, KING))
                .build();

        BullshitDto dto = BullshitDto.forViewer(game, new PlayerId(0));

        BullshitPlayerDto opponent = dto.players().stream()
                .filter(p -> p.id().equals("1")).findFirst().orElseThrow();
        assertThat(opponent.handCount(), is(2));
    }

    @Test
    void givenCurrentPlayer_whenForViewer_thenFlaggedAndActionsForViewerOnly() {
        Bullshit game = aBullshit()
                .withPlayers(playerWithRanks(0, ACE), playerWithRanks(1, TWO))
                .withCurrentPlayerIndex(0)
                .build();

        BullshitDto dto = BullshitDto.forViewer(game, new PlayerId(0));

        BullshitPlayerDto self = dto.players().stream()
                .filter(p -> p.id().equals("0")).findFirst().orElseThrow();
        assertThat(self.isCurrentPlayer(), is(true));
        assertThat(dto.availableActions(), hasItem("DISCARD"));
    }

    @Test
    void givenEliminatedViewer_whenForViewer_thenEmptyHandNoThrow() {
        Bullshit game = aBullshit()
                .withPlayers(playerWithRanks(0, ACE), playerWithRanks(1, TWO), playerWithRanks(2, KING))
                .build();
        game.forfeit(new PlayerId(2)); // seat 2 removed from players

        BullshitDto dto = BullshitDto.forViewer(game, new PlayerId(2));

        assertThat(dto.myHand().isEmpty(), is(true));
        assertThat(dto.availableActions().isEmpty(), is(true));
    }
}
