package org.kevinkib.cardgames.game;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameOptionsTest {

    @Test
    void givenNone_whenGetAnyKey_thenEmpty() {
        assertThat(GameOptions.none().get("claimMode"), is(Optional.empty()));
    }

    @Test
    void givenEmptyMap_whenOf_thenReturnsNoneSingleton() {
        assertThat(GameOptions.of(Map.of()), is(GameOptions.none()));
    }

    @Test
    void givenKey_whenGet_thenValuePresent() {
        GameOptions options = GameOptions.of(Map.of("claimMode", "suit"));
        assertThat(options.get("claimMode"), is(Optional.of("suit")));
    }

    @Test
    void givenConstructed_whenSourceMapMutated_thenOptionsUnchanged() {
        java.util.HashMap<String, String> source = new java.util.HashMap<>();
        source.put("claimMode", "suit");
        GameOptions options = GameOptions.of(source);
        source.put("claimMode", "rank");
        assertThat(options.get("claimMode"), is(Optional.of("suit")));
    }

    @Test
    void givenValues_whenMutateReturnedMap_thenThrows() {
        GameOptions options = GameOptions.of(Map.of("claimMode", "suit"));
        assertThrows(UnsupportedOperationException.class, () -> options.values().put("x", "y"));
    }
}
