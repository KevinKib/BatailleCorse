package org.kevinkib.cardgames.presentation.support;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.presentation.support.StompTestClient.Frames;

import java.time.Duration;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StompTestClientTest {

    private static Map<String, Object> frame(String eventType) {
        return Map.of("eventType", eventType);
    }

    private static java.util.function.Predicate<Map<String, Object>> ofType(String eventType) {
        return f -> eventType.equals(f.get("eventType"));
    }

    @Test
    void givenFrames_whenAwait_thenTheFirstMatchingFrameIsReturnedInArrivalOrder() throws Exception {
        Frames frames = new Frames(Duration.ofMillis(300));
        frames.accept(frame("A"));
        frames.accept(frame("B"));
        frames.accept(frame("B"));

        assertThat(frames.await(ofType("B")), is(frame("B")));
        assertThat(frames.drain().size(), is(1));
    }

    @Test
    void givenNoMatchingFrame_whenAwait_thenItFailsAfterTheTimeout() {
        Frames frames = new Frames(Duration.ofMillis(200));
        frames.accept(frame("A"));

        assertThrows(AssertionError.class, () -> frames.await(ofType("Z")));
    }

    @Test
    void givenFrameSkippedByAnEarlierAwait_whenAwaitAny_thenItIsStillFound() throws Exception {
        Frames frames = new Frames(Duration.ofMillis(300));
        frames.accept(frame("REJECT"));
        frames.accept(frame("MATCH"));
        frames.await(ofType("MATCH"));

        assertThat(frames.awaitAny(ofType("REJECT")), is(frame("REJECT")));
    }

    @Test
    void givenFrameSkippedByAnEarlierAwait_whenAwaitAgain_thenItIsNotSeenAnymore() throws Exception {
        Frames frames = new Frames(Duration.ofMillis(200));
        frames.accept(frame("REJECT"));
        frames.accept(frame("MATCH"));
        frames.await(ofType("MATCH"));

        assertThrows(AssertionError.class, () -> frames.await(ofType("REJECT")));
    }

    @Test
    void givenFramesArrivingLater_whenAwaitAny_thenItWaitsForThem() throws Exception {
        Frames frames = new Frames(Duration.ofSeconds(5));
        Thread producer = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            frames.accept(frame("LATE"));
        });
        producer.start();

        assertThat(frames.awaitAny(ofType("LATE")), is(frame("LATE")));
        producer.join();
    }

    @Test
    void givenConsumedFrames_whenAll_thenEveryFrameReceivedIsStillListed() throws Exception {
        Frames frames = new Frames(Duration.ofMillis(300));
        frames.accept(frame("A"));
        frames.accept(frame("B"));
        frames.await(ofType("B"));

        assertThat(frames.all, contains(frame("A"), frame("B")));
        assertThat(frames.drain(), is(empty()));
    }

    @Test
    void givenArguments_whenBody_thenGameIdTokenAndPairsAreKeyed() {
        Map<String, Object> body = StompTestClient.body("g", "t", "seat", 1, "slot", 2);

        assertThat(body, is(Map.of("gameId", "g", "token", "t", "seat", 1, "slot", 2)));
        assertThat(StompTestClient.body("g", null).get("token"), is(nullValue()));
    }
}
