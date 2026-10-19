package org.kevinkib.cardgames.president.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.RandomDeckSource;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cardgames.president.domain.round.NotPlayersTurnException;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.domain.round.WrongPhaseException;
import org.kevinkib.cardgames.president.domain.rules.MatchLength;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.domain.trick.MustLeadException;
import org.kevinkib.cardgames.president.presentation.dto.PresidentDto;
import org.kevinkib.cardgames.president.presentation.dto.event.PresidentEventData;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.dto.CardDto;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.card;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

class PresidentGameActionsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PresidentTable table = new PresidentTable();

    private static PlayerId seat(int id) {
        return new PlayerId(id);
    }

    private static PresidentEventData data(Response response) {
        return (PresidentEventData) response.getEventData();
    }

    private President threePlayers() {
        return table.install(aPresident().withHand("4C 9C").withHand("5C 6C").withHand("7C 8C").leader(0).build());
    }

    @Test
    void aPlayBroadcastsOneStatePerSeatWithTheTypeAndThePlayedCards() throws Exception {
        President game = threePlayers();

        table.actions.play(game.getId(), seat(0), cards("4C"));

        assertThat(table.messaging.seats, is(List.of(seat(0), seat(1), seat(2))));
        Response first = table.messaging.payloads.get(0);
        assertThat(first.isSuccess(), is(true));
        assertThat(first.getEventType(), is("PLAY"));
        assertThat(data(first).seat(), is(0));
        assertThat(data(first).action(), is("PLAY"));
        assertThat(data(first).cards(), is(List.of(CardDto.from(card("4C")))));
        assertThat(((PresidentDto) first.getState()).phase(), is("PLAYING"));
        assertThat(((PresidentDto) first.getState()).version(), is(1));
        assertThat(((PresidentDto) first.getState()).currentSeat(), is(1));
    }

    @Test
    void eachSeatReceivesItsOwnHand() throws Exception {
        President game = threePlayers();

        table.actions.play(game.getId(), seat(0), cards("4C"));

        PresidentDto mine = (PresidentDto) table.messaging.payloads.get(0).getState();
        PresidentDto theirs = (PresidentDto) table.messaging.payloads.get(1).getState();
        assertThat(mine.hand().size(), is(1));
        assertThat(theirs.hand().get(0), is(CardDto.from(card("5C"))));
    }

    @Test
    void aPassIsBroadcastAsPass() throws Exception {
        President game = threePlayers();
        table.actions.play(game.getId(), seat(0), cards("4C"));
        table.messaging.clear();

        table.actions.pass(game.getId(), seat(1));

        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType(), is("PASS"));
        assertThat(data(first).seat(), is(1));
        assertThat(data(first).cards(), is(nullValue()));
    }

    @Test
    void aRejectedCommandBroadcastsNothingAndPropagates() {
        President game = threePlayers();

        assertThrows(NotPlayersTurnException.class, () -> table.actions.play(game.getId(), seat(1), cards("5C")));
        assertThrows(MustLeadException.class, () -> table.actions.pass(game.getId(), seat(0)));
        assertThrows(WrongPhaseException.class, () -> table.actions.ready(game.getId(), seat(0)));

        assertThat(table.messaging.payloads.isEmpty(), is(true));
        assertThat(game.version(), is(0));
    }

    @Test
    void anAcknowledgementOrACloseThatTheDomainIgnoresBroadcastsNothing() throws Exception {
        President game = threePlayers();

        table.actions.seeExchange(game.getId(), seat(0), 1);
        table.actions.closeExchangeView(game.getId(), 1);

        assertThat(table.messaging.payloads.isEmpty(), is(true));
        assertThat(game.version(), is(0));
    }

    @Test
    void theFirstPlayerOutIsAnnouncedWithItsPlaceAndTheTrickItClosed() throws Exception {
        President game = table.install(aPresident().withHand("4C").withHand("5C 6C").withHand("7C 8C").leader(0)
                .build());

        table.actions.play(game.getId(), seat(0), cards("4C"));

        PresidentEventData data = data(table.messaging.payloads.get(0));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("PLAY"));
        assertThat(data.outPlace(), is(1));
        assertThat(data.trickWinner(), is(1));
        assertThat(data.trickEndReason(), is("FIRST_OUT"));
    }

    @Test
    void aPassThatClosesTheTrickNamesTheWinner() throws Exception {
        President game = threePlayers();
        table.actions.play(game.getId(), seat(0), cards("4C"));
        table.actions.pass(game.getId(), seat(1));
        table.messaging.clear();

        table.actions.pass(game.getId(), seat(2));

        PresidentEventData data = data(table.messaging.payloads.get(0));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("PASS"));
        assertThat(data.trickWinner(), is(0));
        assertThat(data.trickEndReason(), is("ALL_PASSED"));
        assertThat(data.outPlace(), is(nullValue()));
    }

    @Test
    void aCommandEndingTheRoundIsBroadcastAsRoundEndAndStillNamesTheCommand() throws Exception {
        President game = table.install(aPresident().withHand("4C").withHand("5C 6C").withHand("7C 8C 9C").leader(0)
                .build());
        table.actions.play(game.getId(), seat(0), cards("4C"));
        table.actions.play(game.getId(), seat(1), cards("5C"));
        table.actions.pass(game.getId(), seat(2));
        table.messaging.clear();

        table.actions.play(game.getId(), seat(1), cards("6C"));

        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType(), is("ROUND_END"));
        assertThat(data(first).action(), is("PLAY"));
        assertThat(data(first).outPlace(), is(2));
        assertThat(((PresidentDto) first.getState()).phase(), is("ROUND_OVER"));
        assertThat(((PresidentDto) first.getState()).lastRound().finishingOrder(), is(List.of(0, 1, 2)));
    }

    @Test
    void readyIsBroadcastAsReadyUntilTheBarrierOpensAsRoundStart() throws Exception {
        President game = table.install(aPresident().withHand("4C").withHand("5C 6C").withHand("7C 8C 9C").leader(0)
                .build());
        table.actions.play(game.getId(), seat(0), cards("4C"));
        table.actions.play(game.getId(), seat(1), cards("5C"));
        table.actions.pass(game.getId(), seat(2));
        table.actions.play(game.getId(), seat(1), cards("6C"));
        table.messaging.clear();

        table.actions.ready(game.getId(), seat(0));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("READY"));
        assertThat(((PresidentDto) table.messaging.payloads.get(0).getState()).players().get(0).ready(), is(true));
        table.messaging.clear();
        table.actions.ready(game.getId(), seat(1));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("READY"));
        table.messaging.clear();
        table.actions.ready(game.getId(), seat(2));

        Response opened = table.messaging.payloads.get(0);
        assertThat(opened.getEventType(), is("ROUND_START"));
        assertThat(data(opened).action(), is("READY"));
        assertThat(data(opened).barrierOpened(), is(true));
        assertThat(((PresidentDto) opened.getState()).round(), is(2));
        assertThat(game.phase(), is(Phase.EXCHANGE));
    }

    @Test
    void aGiveBackIsBroadcastAsExchangeWithTheCountAndNeverTheCards() throws Exception {
        President game = table.install(aPresident().withHand("4C").withHand("5C 6C").withHand("7C 8C 9C").leader(0)
                .build());
        table.actions.play(game.getId(), seat(0), cards("4C"));
        table.actions.play(game.getId(), seat(1), cards("5C"));
        table.actions.pass(game.getId(), seat(2));
        table.actions.play(game.getId(), seat(1), cards("6C"));
        for (int s = 0; s < 3; s++) {
            table.actions.ready(game.getId(), seat(s));
        }
        PlayerId president = game.exchange().get(0).high();
        List<org.kevinkib.cards.domain.Card> back = new ArrayList<>(game.hand(president).subList(0, 2));
        table.messaging.clear();

        table.actions.giveBack(game.getId(), president, back);

        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType(), is("EXCHANGE"));
        assertThat(data(first).count(), is(2));
        assertThat(data(first).cards(), is(nullValue()));
        PlayerId scum = game.exchange().get(0).low();
        for (int i = 0; i < table.messaging.payloads.size(); i++) {
            PlayerId receiver = table.messaging.seats.get(i);
            if (!receiver.equals(president) && !receiver.equals(scum)) {
                String bystander = MAPPER.writeValueAsString(table.messaging.payloads.get(i));
                assertThat(bystander, not(containsString("\"name\":\"" + CardDto.from(back.get(0)).name() + "\"")));
            }
        }
    }

    @Test
    void theCommandThatEndsTheMatchIsBroadcastAsGameOverOnceAndOnlyThen() throws Exception {
        President game = table.install(new President(GameId.generate(), 3, 9L, new RandomDeckSource(),
                PresidentOptions.defaults().withLength(MatchLength.ROUNDS_3)));
        int rounds = 0;
        for (int step = 0; step < 5000 && !game.isFinished(); step++) {
            if (game.phase() == Phase.PLAYING) {
                PlayerId current = game.currentSeat();
                List<Play> options = game.legalPlays(current);
                if (game.trick().plays().isEmpty() || !options.isEmpty()) {
                    table.actions.play(game.getId(), current, options.get(0).cards());
                } else {
                    table.actions.pass(game.getId(), current);
                }
            } else if (game.phase() == Phase.EXCHANGE) {
                var pending = game.pendingReturns().get(0);
                table.actions.giveBack(game.getId(), pending.high(),
                        game.hand(pending.high()).subList(0, pending.count()));
            } else {
                for (PlayerId s : game.getPlayerIds()) {
                    if (!game.readySeats().contains(s)) {
                        table.actions.ready(game.getId(), s);
                        break;
                    }
                }
                rounds++;
            }
        }

        List<String> types = table.messaging.payloads.stream().map(Response::getEventType).toList();
        assertThat(game.isFinished(), is(true));
        assertThat(types.stream().filter("GAME_OVER"::equals).count(), is(3L)); // one per seat, one command
        assertThat(types.get(types.size() - 1), is("GAME_OVER"));
        assertThat(types.stream().filter("ROUND_END"::equals).count(), is(6L)); // rounds 1 and 2: 3 seats each
        assertThat(rounds > 0, is(true));
        PresidentDto last = (PresidentDto) table.messaging.payloads.get(table.messaging.payloads.size() - 1)
                .getState();
        assertThat(last.phase(), is("FINISHED"));
    }

    @Test
    void broadcastVersionsNeverGoBackwardsWhenSeatsRace() throws Exception {
        for (int run = 0; run < 30; run++) {
            table.messaging.clear();
            President game = table.install(aPresident().withHand("4C 9C").withHand("5C 6C").withHand("7C 8C")
                    .leader(0).build());
            ExecutorService pool = Executors.newFixedThreadPool(3);
            CountDownLatch go = new CountDownLatch(1);
            List<Runnable> attempts = List.of(
                    () -> attempt(game, 0, "4C"), () -> attempt(game, 1, "5C"), () -> attempt(game, 2, "7C"));
            for (Runnable attempt : attempts) {
                pool.submit(() -> {
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        return;
                    }
                    attempt.run();
                });
            }
            go.countDown();
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);

            List<Integer> versions = new ArrayList<>();
            synchronized (table.messaging.payloads) {
                for (int i = 0; i < table.messaging.payloads.size(); i++) {
                    if (table.messaging.seats.get(i).equals(seat(0))) {
                        versions.add(((PresidentDto) table.messaging.payloads.get(i).getState()).version());
                    }
                }
            }
            for (int i = 1; i < versions.size(); i++) {
                assertThat(versions.get(i), greaterThanOrEqualTo(versions.get(i - 1)));
            }
        }
    }

    private void attempt(President game, int seat, String played) {
        try {
            table.actions.play(game.getId(), seat(seat), cards(played));
        } catch (Exception expectedRefusal) {
            // only the seat whose turn it is succeeds
        }
    }
}
