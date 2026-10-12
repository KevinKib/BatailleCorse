package org.kevinkib.cardgames.sessionmanagement.core.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class SessionTokenTest {

    @Test
    void givenAWellFormedUuid_whenParsed_thenItEqualsTheGeneratedToken() {
        SessionToken generated = SessionToken.generate();

        assertThat(new SessionToken(generated.uuid().toString()), is(generated));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"stale-token", "", " ", "not-a-uuid-at-all", "12345678-1234-1234-1234"})
    void givenAMalformedValue_whenParsed_thenItIsAnInvalidTokenThatMatchesNoGeneratedOne(String malformed) {
        SessionToken token = new SessionToken(malformed);

        assertThat(token, is(not(SessionToken.generate())));
        assertThat(token, is(new SessionToken(malformed)));
    }

    @Test
    void givenAVeryLongValue_whenParsed_thenItIsAnInvalidToken() {
        String veryLong = "a".repeat(100_000);

        assertThat(new SessionToken(veryLong), is(not(new SessionToken(UUID.randomUUID().toString()))));
    }
}
