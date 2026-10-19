package org.kevinkib.cardgames.president.domain;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.event.TrickWon;
import org.kevinkib.cardgames.president.domain.trick.TrickView;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.president.domain.PresidentBuilder.aPresident;
import static org.kevinkib.cardgames.president.domain.PresidentFixtures.cards;

/**
 * The worked examples 1 to 8 of the spec (3.1.2): four players A, B, C, D (seats 0 to 3), {@code equalPlay = on},
 * {@code afterPass = reenter}. Each row is the starting hands, the seat that leads and the commands
 * ({@code seat:cards} with {@code +} between cards, {@code seat:pass}); every command carries what it must answer
 * ({@code ok}, {@code closed:REASON:leader} or the name of the refusal) and the state it leaves:
 * {@code cur} the seat to act, {@code plays} on the trick, {@code bound} whether the next play must match,
 * {@code chain} the rank and the number of cards played in succession at the top, {@code open} whether the
 * completion window is open, {@code holder} the seats that may complete. A refused command also leaves the
 * version where it was.
 */
class EqualPlayExamplesTest {

    private record Step(String command, String answer, String state) {
    }

    private static Step step(String command, String answer, String state) {
        return new Step(command, answer, state);
    }

    private static Arguments example(String name, String hands, int leader, Step... steps) {
        return Arguments.of(Named.of(name, hands), leader, List.of(steps));
    }

    static Stream<Arguments> examples() {
        return Stream.of(
                example("1 singles chain closed in turn, D completes",
                        "9S 3C | 9H 4C | 9D 5C | 9C 6C", 0,
                        step("0:9S", "ok", "cur=1 plays=1 bound=no chain=9x1 open=no holder=-"),
                        step("1:9H", "ok", "cur=2 plays=2 bound=yes chain=9x2 open=yes holder=-"),
                        step("2:9D", "ok", "cur=3 plays=3 bound=yes chain=9x3 open=yes holder=3"),
                        step("3:9C", "closed:QUAD:3", "cur=3 plays=0 bound=no chain=- open=no holder=-")),
                example("1b singles chain: D holds no 9 and cannot play higher, only pass",
                        "9S 3C | 9H 4C | 9D 5C | 10C 6C", 0,
                        step("0:9S", "ok", "cur=1 plays=1 bound=no chain=9x1 open=no holder=-"),
                        step("1:9H", "ok", "cur=2 plays=2 bound=yes chain=9x2 open=yes holder=-"),
                        step("2:9D", "ok", "cur=3 plays=3 bound=yes chain=9x3 open=yes holder=-"),
                        step("3:10C", "PlayDoesNotBeatException", "cur=3 plays=3 bound=yes chain=9x3 open=yes holder=-"),
                        step("3:pass", "ok", "cur=0 plays=3 bound=no chain=9x3 open=yes holder=-")),
                example("2 pair chain: the second pair closes the trick at once, B leads",
                        "4S 4H 3C | 4D 4C 5C | 8C 8D | 9C 9D", 0,
                        step("0:4S+4H", "ok", "cur=1 plays=1 bound=no chain=4x2 open=no holder=-"),
                        step("1:4D+4C", "closed:QUAD:1", "cur=1 plays=0 bound=no chain=- open=no holder=-")),
                example("3 B, who already played in the chain, completes before D acts",
                        "9S 3C | 9H 9C 4C | 9D 5C | 6C 7C", 0,
                        step("0:9S", "ok", "cur=1 plays=1 bound=no chain=9x1 open=no holder=-"),
                        step("1:9H", "ok", "cur=2 plays=2 bound=yes chain=9x2 open=yes holder=-"),
                        step("2:9D", "ok", "cur=3 plays=3 bound=yes chain=9x3 open=yes holder=1"),
                        step("1:9C", "closed:QUAD:1", "cur=1 plays=0 bound=no chain=- open=no holder=-")),
                example("4 C, who passed, completes with two cards on single 9s",
                        "9S 3C | 9H 4C | 9D 9C 5C | 6C 7C", 0,
                        step("0:9S", "ok", "cur=1 plays=1 bound=no chain=9x1 open=no holder=-"),
                        step("1:9H", "ok", "cur=2 plays=2 bound=yes chain=9x2 open=yes holder=2"),
                        step("2:pass", "ok", "cur=3 plays=2 bound=no chain=9x2 open=yes holder=2"),
                        step("3:pass", "ok", "cur=0 plays=2 bound=no chain=9x2 open=yes holder=2"),
                        step("2:9D+9C", "closed:QUAD:2", "cur=2 plays=0 bound=no chain=- open=no holder=-")),
                example("5 D passes, then B completes: a pass does not close the window",
                        "9S 3C | 9H 9C 4C | 9D 5C | 6C 7C", 0,
                        step("0:9S", "ok", "cur=1 plays=1 bound=no chain=9x1 open=no holder=-"),
                        step("1:9H", "ok", "cur=2 plays=2 bound=yes chain=9x2 open=yes holder=-"),
                        step("2:9D", "ok", "cur=3 plays=3 bound=yes chain=9x3 open=yes holder=1"),
                        step("3:pass", "ok", "cur=0 plays=3 bound=no chain=9x3 open=yes holder=1"),
                        step("1:9C", "closed:QUAD:1", "cur=1 plays=0 bound=no chain=- open=no holder=-")),
                example("6 the first pass ends the binding, D beats the 9s with a 10 and the window closes",
                        "9S 3C | 9H 4C | 9D 9C 5C | 10H 6C", 0,
                        step("0:9S", "ok", "cur=1 plays=1 bound=no chain=9x1 open=no holder=-"),
                        step("1:9H", "ok", "cur=2 plays=2 bound=yes chain=9x2 open=yes holder=2"),
                        step("2:pass", "ok", "cur=3 plays=2 bound=no chain=9x2 open=yes holder=2"),
                        step("3:10H", "ok", "cur=0 plays=3 bound=no chain=10x1 open=no holder=-"),
                        step("2:9D+9C", "CompletionClosedException", "cur=0 plays=3 bound=no chain=10x1 open=no holder=-")),
                example("7 after the first pass D is free and plays an equal 9: A is bound and completes",
                        "9S 9C 3C | 9H 4C | 5C 6C | 9D 10H", 0,
                        step("0:9S", "ok", "cur=1 plays=1 bound=no chain=9x1 open=no holder=-"),
                        step("1:9H", "ok", "cur=2 plays=2 bound=yes chain=9x2 open=yes holder=-"),
                        step("2:pass", "ok", "cur=3 plays=2 bound=no chain=9x2 open=yes holder=-"),
                        step("3:9D", "ok", "cur=0 plays=3 bound=yes chain=9x3 open=yes holder=0"),
                        step("0:9C", "closed:QUAD:0", "cur=0 plays=0 bound=no chain=- open=no holder=-")),
                example("7b ... had A passed, that pass ends the binding again and B is free to play a 10",
                        "9S 9C JC | 9H 10C 4C | 5C 6C | 9D 10H", 0,
                        step("0:9S", "ok", "cur=1 plays=1 bound=no chain=9x1 open=no holder=-"),
                        step("1:9H", "ok", "cur=2 plays=2 bound=yes chain=9x2 open=yes holder=-"),
                        step("2:pass", "ok", "cur=3 plays=2 bound=no chain=9x2 open=yes holder=-"),
                        step("3:9D", "ok", "cur=0 plays=3 bound=yes chain=9x3 open=yes holder=0"),
                        step("0:JC", "PlayDoesNotBeatException", "cur=0 plays=3 bound=yes chain=9x3 open=yes holder=0"),
                        step("0:pass", "ok", "cur=1 plays=3 bound=no chain=9x3 open=yes holder=0"),
                        step("1:10C", "ok", "cur=2 plays=4 bound=no chain=10x1 open=no holder=-")),
                example("8 a single lead does not open the window out of turn: the triple is refused out of turn, and completes in turn",
                        "9S 3C | 4C 5C | 9H 9D 9C 6C | 7C 8C", 0,
                        step("0:9S", "ok", "cur=1 plays=1 bound=no chain=9x1 open=no holder=-"),
                        step("2:9H+9D+9C", "NotPlayersTurnException", "cur=1 plays=1 bound=no chain=9x1 open=no holder=-"),
                        step("1:pass", "ok", "cur=2 plays=1 bound=no chain=9x1 open=no holder=-"),
                        step("2:9H+9D+9C", "closed:QUAD:2", "cur=2 plays=0 bound=no chain=- open=no holder=-")),
                example("8b ... after B's equal 9 the holder of the two missing 9s may complete (as in 4)",
                        "9S 3C | 9H 4C | 9D 9C 6C | 7C 8C", 0,
                        step("0:9S", "ok", "cur=1 plays=1 bound=no chain=9x1 open=no holder=-"),
                        step("1:9H", "ok", "cur=2 plays=2 bound=yes chain=9x2 open=yes holder=2"),
                        step("2:9D+9C", "closed:QUAD:2", "cur=2 plays=0 bound=no chain=- open=no holder=-")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("examples")
    void theWorkedExamplesOfTheSpec(String hands, int leader, List<Step> steps) throws Exception {
        PresidentBuilder builder = aPresident().leader(leader);
        for (String hand : hands.split("\\|")) {
            builder.withHand(hand.trim());
        }
        President game = builder.build();

        for (Step step : steps) {
            int version = game.version();
            String answer = run(game, step.command());
            assertThat(step.command() + " answers", answer, is(step.answer()));
            if (!step.answer().equals("ok") && !step.answer().startsWith("closed")) {
                assertThat(step.command() + " leaves the version", game.version(), is(version));
            }
            assertThat("state after " + step.command(), describe(game), is(step.state()));
            assertThat("the deck stays whole after " + step.command(), cardsInGame(game), is(hands.replace("|", " ")
                    .trim().split("\\s+").length));
        }
    }

    private static int cardsInGame(President game) {
        int total = game.discard().size();
        for (int seat = 0; seat < game.handSizes().size(); seat++) {
            total += game.hand(new PlayerId(seat)).size();
        }
        for (var play : game.trick().plays()) {
            total += play.play().cards().size();
        }
        return total;
    }

    private static String run(President game, String command) {
        String[] parts = command.split(":");
        PlayerId seat = new PlayerId(Integer.parseInt(parts[0]));
        try {
            List<PresidentEvent> events = parts[1].equals("pass")
                    ? game.pass(seat)
                    : game.play(seat, cards(parts[1].replace('+', ' ')));
            for (PresidentEvent event : events) {
                if (event instanceof TrickWon won) {
                    return "closed:" + won.reason() + ":" + won.seat().id();
                }
            }
            return "ok";
        } catch (Exception e) {
            return e.getClass().getSimpleName();
        }
    }

    private static String describe(President game) {
        TrickView trick = game.trick();
        List<Integer> holders = new ArrayList<>();
        for (int seat = 0; seat < game.handSizes().size(); seat++) {
            if (!game.completion(new PlayerId(seat)).isEmpty()) {
                holders.add(seat);
            }
        }
        String chain = trick.chain() == null ? "-"
                : PresidentFixtures.code(trick.chain().cards().get(0)).replaceAll("[HDCS]$", "")
                + "x" + trick.chain().cards().size();
        boolean open = trick.chain() != null && trick.chain().plays() >= 2;
        return "cur=" + game.currentSeat().id() + " plays=" + trick.plays().size()
                + " bound=" + (trick.constraint().name().equals("MATCH_OR_PASS") ? "yes" : "no")
                + " chain=" + chain
                + " open=" + (open ? "yes" : "no")
                + " holder=" + (holders.isEmpty() ? "-" : String.join(",", holders.stream().map(String::valueOf).toList()));
    }
}
