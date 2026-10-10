package org.kevinkib.cardgames.bullshit.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Action;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.claim.AscendingRankClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.CyclingSuitClaimMode;
import org.kevinkib.cardgames.bullshit.domain.pile.Discard;
import org.kevinkib.cardgames.bullshit.domain.player.Player;
import org.kevinkib.cardgames.bullshit.presentation.dto.BullshitDto;
import org.kevinkib.cardgames.bullshit.presentation.dto.BullshitPlayerDto;
import org.kevinkib.cardgames.bullshit.presentation.dto.CardDto;
import org.kevinkib.cardgames.bullshit.presentation.dto.PendingWinnerDto;
import org.kevinkib.cardgames.bullshit.presentation.dto.TableDto;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.hand.Hand;

import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.bullshit.domain.BullshitBuilder.aBullshit;
import static org.kevinkib.cardgames.bullshit.domain.BullshitFixtures.playerWithRanks;

class BotObservationTest {

    private static Bullshit threePlayerGame() {
        return threePlayerGame(new AscendingRankClaimMode());
    }

    private static Bullshit threePlayerGame(ClaimMode claimMode) {
        return aBullshit()
                .withClaimMode(claimMode)
                .withPlayers(
                        playerWithRanks(0, FrenchRank.ACE, FrenchRank.KING),
                        playerWithRanks(1, FrenchRank.TWO, FrenchRank.TWO, FrenchRank.NINE),
                        playerWithRanks(2, FrenchRank.THREE))
                .build();
    }

    @Test
    void givenFreshGame_whenObservingTheStartingSeat_thenOwnHandCountsTargetAndActions() {
        Bullshit game = threePlayerGame();

        BotObservation obs = BotObservation.forSeat(game, new PlayerId(0));

        assertThat(obs.me(), is(new PlayerId(0)));
        assertThat(obs.hand(), is(game.getPlayers().get(0).getCards()));
        assertThat(obs.handCounts().get(new PlayerId(0)), is(2));
        assertThat(obs.handCounts().get(new PlayerId(1)), is(3));
        assertThat(obs.handCounts().get(new PlayerId(2)), is(1));
        assertThat(obs.currentTarget(), is(game.getCurrentTarget()));
        assertThat(obs.pileSize(), is(0));
        assertThat(obs.lastClaim().isPresent(), is(false));
        assertThat(obs.pendingWinner().isPresent(), is(false));
        assertThat(obs.currentPlayer(), is(new PlayerId(0)));
        assertThat(obs.actions(), contains(Action.DISCARD));
    }

    @Test
    void givenClaimOnTable_whenObservingAnotherSeat_thenClaimHasCountButNoCards() throws Exception {
        Bullshit game = threePlayerGame();
        game.discard(new PlayerId(0), game.getPlayers().get(0).getCards().subList(0, 1));

        BotObservation obs = BotObservation.forSeat(game, new PlayerId(2));

        assertThat(obs.lastClaim().orElseThrow().claimant(), is(new PlayerId(0)));
        assertThat(obs.lastClaim().orElseThrow().target(), is(game.getLastDiscard().orElseThrow().claimedTarget()));
        assertThat(obs.lastClaim().orElseThrow().count(), is(1));
        assertThat(obs.pileSize(), is(1));
        assertThat(obs.actions(), contains(Action.CALL_BULLSHIT));
    }

    @Test
    void givenSeatNotInGame_whenObserving_thenRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> BotObservation.forSeat(threePlayerGame(), new PlayerId(7)));
    }

    @Test
    void givenObservation_thenHandAndCountsAreImmutableCopies() {
        BotObservation obs = BotObservation.forSeat(threePlayerGame(), new PlayerId(0));

        assertThrows(UnsupportedOperationException.class, () -> obs.hand().clear());
        assertThrows(UnsupportedOperationException.class, () -> obs.handCounts().clear());
    }

    @Test
    void givenSameGameAndSeat_whenComparedWithDto_thenEverySharedFactAgrees() throws Exception {
        for (ClaimMode claimMode : List.of(new AscendingRankClaimMode(), new CyclingSuitClaimMode())) {
            Bullshit game = threePlayerGame(claimMode);
            assertParity(game);
            game.discard(new PlayerId(0), game.getPlayers().get(0).getCards().subList(0, 1));
            assertParity(game);
            game.discard(new PlayerId(1), game.getPlayers().get(1).getCards().subList(0, 1));
            assertParity(game);
            game.discard(new PlayerId(2), game.getPlayers().get(2).getCards()); // empties hand: pending winner
            assertParity(game);
            game.callBullshit(new PlayerId(0));
            assertParity(game);
        }
    }

    private static void assertParity(Bullshit game) {
        for (PlayerId seat : game.getPlayerIds()) {
            BullshitDto dto = BullshitDto.forViewer(game, seat);
            BotObservation obs = BotObservation.forSeat(game, seat);

            assertThat(obs.hand().stream().map(CardDto::from).toList(), is(dto.myHand()));
            assertThat(obs.actions().stream().map(Action::name).toList(), is(dto.availableActions()));
            assertThat(obs.currentTarget().label(), is(dto.currentTarget().label()));
            assertThat(obs.pileSize(), is(dto.discardPileSize()));
            assertThat(obs.handCounts().size(), is(dto.players().size()));
            for (BullshitPlayerDto p : dto.players()) {
                PlayerId id = new PlayerId(Integer.parseInt(p.id()));
                assertThat(obs.handCounts().get(id), is(p.handCount()));
                assertThat(obs.currentPlayer().equals(id), is(p.isCurrentPlayer()));
            }
            if (dto.table() instanceof TableDto.Claim claim) {
                BotObservation.Claim seen = obs.lastClaim().orElseThrow();
                assertThat(String.valueOf(seen.claimant().id()), is(claim.claimantId()));
                assertThat(seen.target().label(), is(claim.claimedTargetLabel()));
                assertThat(seen.count(), is(claim.count()));
            } else {
                assertThat(obs.lastClaim().isPresent(), is(false));
            }
            if (dto.pendingWinner() instanceof PendingWinnerDto.Pending pending) {
                assertThat(String.valueOf(obs.pendingWinner().orElseThrow().id()), is(pending.playerId()));
            } else {
                assertThat(obs.pendingWinner().isPresent(), is(false));
            }
        }
    }

    @Test
    void givenObservationRecords_thenNoComponentCanLeakSecretCards() {
        List<Class<?>> secretTypes = List.of(Hand.class, Player.class, Discard.class, Bullshit.class);

        for (RecordComponent component : BotObservation.class.getRecordComponents()) {
            String type = component.getGenericType().getTypeName();
            assertThat(component.getName(), secretTypes.stream().noneMatch(t -> type.contains(t.getName())), is(true));
        }
        for (RecordComponent component : BotObservation.Claim.class.getRecordComponents()) {
            String type = component.getGenericType().getTypeName();
            assertThat(component.getName(), type.contains(Card.class.getName()), is(false));
        }
    }

    @Test
    void givenFinishedGame_whenObserving_thenNoActions() {
        Bullshit game = threePlayerGame();
        game.forfeit(new PlayerId(1));
        game.forfeit(new PlayerId(2));

        assertThat(BotObservation.forSeat(game, new PlayerId(0)).actions(), is(empty()));
    }
}
