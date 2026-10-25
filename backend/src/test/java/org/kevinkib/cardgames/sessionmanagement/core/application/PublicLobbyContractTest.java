package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.sessionmanagement.core.domain.LobbyVisibility;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

/** Shape of the public game list vocabulary; the behaviour is covered by the other public-list tests. */
class PublicLobbyContractTest {

    @Test
    void visibilityIsPublicOrPrivate() {
        assertThat(List.of(LobbyVisibility.values()), contains(LobbyVisibility.PUBLIC, LobbyVisibility.PRIVATE));
    }

    @Test
    void publicLobbyStatesAreOpenFullAndInPlay() {
        assertThat(List.of(PublicLobbyState.values()),
                contains(PublicLobbyState.OPEN, PublicLobbyState.FULL, PublicLobbyState.IN_PLAY));
    }
}
