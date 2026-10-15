package org.kevinkib.cardgames.config;

import org.kevinkib.cardgames.president.domain.DeckSource;
import org.kevinkib.cardgames.president.domain.President;
import org.kevinkib.cardgames.president.domain.PresidentFactory;
import org.kevinkib.cardgames.president.domain.RandomDeckSource;
import org.kevinkib.cardgames.president.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.president.presentation.PresidentGameActions;
import org.kevinkib.cardgames.president.presentation.dto.PresidentDto;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.state.SeatLifecycleBroadcaster;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** The beans of the President context: factory, deal source, broadcasters and actions. No bots yet. */
@Configuration
public class PresidentConfig {

    /** Production: a random deal. */
    @Bean
    @Profile("!test")
    public DeckSource presidentDeckSource() {
        return new RandomDeckSource();
    }

    /** End-to-end tests (profile {@code test}): random unless a test fixes the deal, see PresidentTestDealController. */
    @Bean
    @Profile("test")
    public ScriptedDeckSource presidentScriptedDeckSource() {
        return new ScriptedDeckSource(new RandomDeckSource());
    }

    @Bean
    public PresidentFactory presidentFactory(DeckSource presidentDeckSource) {
        return new PresidentFactory(presidentDeckSource);
    }

    @Bean
    public SeatStateBroadcaster<President, PresidentDto> presidentStateBroadcaster(
            GameMessagingService gameMessagingService, SessionService sessionService) {
        return new SeatStateBroadcaster<>(gameMessagingService, sessionService::botSeats, sessionService::seatNames,
                (game, viewer, names, bots) -> PresidentDto.forViewer(game, viewer, names, bots));
    }

    @Bean
    public PresidentGameActions presidentGameActions(SessionService sessionService,
            SeatStateBroadcaster<President, PresidentDto> presidentStateBroadcaster) {
        return new PresidentGameActions(sessionService, presidentStateBroadcaster);
    }

    @Bean
    public GameLifecycleBroadcaster presidentLifecycleBroadcaster(
            SeatStateBroadcaster<President, PresidentDto> presidentStateBroadcaster) {
        return new SeatLifecycleBroadcaster<>(President.class, presidentStateBroadcaster);
    }
}
