package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.play.IllegalPlayException;
import org.kevinkib.cardgames.president.domain.play.PlayDoesNotBeatException;
import org.kevinkib.cardgames.president.domain.round.CardsNotInHandException;
import org.kevinkib.cardgames.president.domain.round.NotPlayersTurnException;
import org.kevinkib.cardgames.president.domain.rules.PresidentException;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/** A refused command never mutates the game: same version, same hands, same turn, same trick. */
class PresidentCommandGuardsTest {

    private static PlayerId p(int seat) {
        return new PlayerId(seat);
    }

    private static President game() {
        return aPresident().withHand("3C 9C 9D").withHand("4C 10C").withHand("5C JC").leader(0).build();
    }

    private record Snapshot(int version, List<Integer> sizes, PlayerId current, int trickPlays, int discarded) {
        static Snapshot of(President g) {
            return new Snapshot(g.version(), g.handSizes(), g.currentSeat(), g.trick().plays().size(),
                    g.discard().size());
        }
    }

    private interface Command {
        void run(President game) throws PresidentException;
    }

    private static void assertRefusedWithoutEffect(President game, Class<? extends PresidentException> type,
                                                   Command command) {
        Snapshot before = Snapshot.of(game);

        assertThrows(type, () -> command.run(game));

        assertThat(Snapshot.of(game), is(before));
    }

    @Test
    void aSeatThatIsNotCurrentCannotPlayOrPass() {
        President game = game();

        assertRefusedWithoutEffect(game, NotPlayersTurnException.class, g -> g.play(p(1), cards("4C")));
        assertRefusedWithoutEffect(game, NotPlayersTurnException.class, g -> g.pass(p(1)));
    }

    @Test
    void anUnknownSeatIsNotThePlayerWhoseTurnItIs() {
        President game = game();

        assertRefusedWithoutEffect(game, NotPlayersTurnException.class, g -> g.play(p(9), cards("4C")));
        assertRefusedWithoutEffect(game, NotPlayersTurnException.class, g -> g.pass(null));
    }

    @Test
    void cardsThatAreNotInTheHandAreRefused() {
        President game = game();

        assertRefusedWithoutEffect(game, CardsNotInHandException.class, g -> g.play(p(0), cards("4C")));
        assertRefusedWithoutEffect(game, CardsNotInHandException.class, g -> g.play(p(0), cards("3C 4C")));
    }

    @Test
    void namingACardTwiceNeedsTwoCopiesInTheHand() {
        President game = game();

        assertRefusedWithoutEffect(game, CardsNotInHandException.class, g -> g.play(p(0), cards("3C 3C")));
    }

    @Test
    void cardsThatDoNotFormAPlayAreRefused() {
        President game = game();

        assertRefusedWithoutEffect(game, IllegalPlayException.class, g -> g.play(p(0), cards("3C 9C")));
        assertRefusedWithoutEffect(game, IllegalPlayException.class, g -> g.play(p(0), List.of()));
        assertRefusedWithoutEffect(game, IllegalPlayException.class, g -> g.play(p(0), null));
    }

    @Test
    void aPlayThatDoesNotBeatIsRefused() throws Exception {
        President game = game();
        game.play(p(0), cards("9C"));

        assertRefusedWithoutEffect(game, PlayDoesNotBeatException.class, g -> g.play(p(1), cards("4C")));
    }

    @Test
    void everyAcceptedCommandBumpsTheVersionByOne() throws Exception {
        President game = game();
        int version = game.version();

        game.play(p(0), cards("9C"));
        assertThat(game.version(), is(version + 1));
        game.pass(p(1));
        assertThat(game.version(), is(version + 2));
    }

    @Test
    void onceTheRoundIsOverNothingCanBePlayed() throws Exception {
        President game = aPresident().withHand("4C").withHand("5C").withHand("6C").leader(0).build();
        game.play(p(0), cards("4C"));
        game.play(p(1), cards("5C"));
        assertThat(game.isFinished(), is(true));

        assertRefusedWithoutEffect(game, FinishedGameException.class, g -> g.play(p(2), cards("6C")));
        assertRefusedWithoutEffect(game, FinishedGameException.class, g -> g.pass(p(2)));
    }

    @Test
    void theBuilderGameStartsUnfinishedWithTheLeaderToAct() {
        President game = game();

        assertThat(game.isFinished(), is(false));
        assertThat(game.currentSeat(), is(p(0)));
    }
}
