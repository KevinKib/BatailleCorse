package org.kevinkib.cardgames.president.domain.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cardgames.president.domain.play.PlayParser;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

class PresidentBotMemoryTest {

    private static final PlayerId BOT = new PlayerId(1);

    @Test
    void itRecordsTheCardsPlayedOnTheTrickAsTheyAppear() throws Exception {
        President game = aPresident().withHand("5H 9D KC").withHand("6H 6D AS").withHand("3C 7S 10H").leader(0).build();
        PresidentBotMemory memory = new PresidentBotMemory(BOT);

        game.play(new PlayerId(0), cards("5H"));
        memory.observe(PresidentBotView.forSeat(game, BOT));
        game.play(BOT, cards("6H"));
        memory.observe(PresidentBotView.forSeat(game, BOT));

        assertThat(memory.played(), containsInAnyOrder(cards("5H 6H").toArray()));
    }

    @Test
    void observingTheSameViewTwiceRecordsNothingNew() throws Exception {
        President game = aPresident().withHand("5H 9D").withHand("6H 6D").withHand("3C 7S").leader(0).build();
        PresidentBotMemory memory = new PresidentBotMemory(BOT);
        game.play(new PlayerId(0), cards("5H"));
        PresidentBotView view = PresidentBotView.forSeat(game, BOT);

        memory.observe(view);
        memory.observe(view);

        assertThat(memory.played().size(), is(1));
    }

    @Test
    void itRecordsTheCardsReceivedAndGivenInTheExchange() {
        PresidentBotMemory memory = new PresidentBotMemory(new PlayerId(0));
        PresidentBotView view = PresidentBotFixtures.givingBack("3H 4D 2S AH", 2);
        PresidentBotView withExchange = new PresidentBotView(view.seat(), view.version(), view.round(), view.phase(),
                view.currentSeat(), view.actions(), view.hand(), view.handSizes(), view.trick(), view.legalPlays(),
                view.finishingOrder(), view.titles(), view.pendingReturns(), cards("2S AH"), cards("3H 3D"),
                view.readySeats(), view.totals(), view.options());

        memory.observe(withExchange);

        assertThat(memory.received(), is(cards("2S AH")));
        assertThat(memory.given(), is(cards("3H 3D")));
    }

    @Test
    void itForgetsEverythingWhenANewRoundIsDealt() {
        PresidentBotMemory memory = new PresidentBotMemory(new PlayerId(0));
        PresidentBotView roundTwo = PresidentBotFixtures.givingBack("3H 4D 2S AH", 2);
        memory.learnPlayed(cards("5H 6H"));
        memory.observe(roundTwo);
        assertThat(memory.round(), is(2));
        memory.learnPlayed(cards("7H"));

        PresidentBotView roundThree = new PresidentBotView(roundTwo.seat(), roundTwo.version() + 1, 3,
                roundTwo.phase(), roundTwo.currentSeat(), roundTwo.actions(), roundTwo.hand(), roundTwo.handSizes(),
                roundTwo.trick(), roundTwo.legalPlays(), roundTwo.finishingOrder(), roundTwo.titles(),
                roundTwo.pendingReturns(), List.of(), List.of(), roundTwo.readySeats(), roundTwo.totals(),
                roundTwo.options());
        memory.observe(roundThree);

        assertThat(memory.round(), is(3));
        assertThat(memory.played(), is(empty()));
        assertThat(memory.received(), is(empty()));
        assertThat(memory.given(), is(empty()));
    }

    @Test
    void theUnseenCardsOfARankAreTheOnesNeitherInHandNorPlayed() {
        PresidentBotMemory memory = new PresidentBotMemory(BOT);
        memory.learnPlayed(cards("KH KD"));

        assertThat(memory.unseen(FrenchRank.KING, cards("KC 3H")), is(1));
        assertThat(memory.unseen(FrenchRank.ACE, cards("KC 3H")), is(4));
    }

    private static Play play(String codes) throws Exception {
        return PlayParser.parse(cards(codes));
    }

    @Test
    void aSetNobodyCanBeatIsUnbeatableOnlyWithinTheRanksTheLevelCounts() throws Exception {
        PresidentBotMemory memory = new PresidentBotMemory(BOT);
        memory.learnPlayed(cards("QH QD QC QS KH KD KC KS AH AD AC AS 2H 2D 2C 2S"));
        RankOrder order = RankOrder.standard();

        // queen, king, ace and two are all gone: a jack cannot be beaten
        assertThat(memory.isUnbeatable(play("JH"), cards("JH 5C"), 13, order), is(true));
        // a level that counts only the top three ranks (K, A, 2) cannot rule the queens out
        assertThat(memory.isUnbeatable(play("JH"), cards("JH 5C"), 3, order), is(false));
        // a level that counts nothing never knows
        assertThat(memory.isUnbeatable(play("JH"), cards("JH 5C"), 0, order), is(false));
        // the tens above a nine are still unseen
        assertThat(memory.isUnbeatable(play("9H"), cards("9H 5C"), 13, order), is(false));
    }

    @Test
    void aPairIsUnbeatableWhenNoHigherPairCanStillBeFormed() throws Exception {
        PresidentBotMemory memory = new PresidentBotMemory(BOT);
        memory.learnPlayed(cards("KH KD KC AH AD AC 2H 2D 2C"));

        assertThat(memory.isUnbeatable(play("QH QD"), cards("QH QD 5C"), 13, RankOrder.standard()), is(true));
        assertThat(memory.isUnbeatable(play("QH"), cards("QH QD 5C"), 13, RankOrder.standard()), is(false));
    }
}
