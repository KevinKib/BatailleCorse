package org.kevinkib.bataillecorse.config;

import org.kevinkib.bataillecorse.sessionmanagement.application.GameCleanupService;
import org.kevinkib.bataillecorse.sessionmanagement.application.SessionService;
import org.kevinkib.bataillecorse.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.bataillecorse.sessionmanagement.infrastructure.InMemorySessionRepository;
import org.kevinkib.bataillecorse.websocket.presentation.v1.StompSessionSeatRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;

@Configuration
@EnableScheduling
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SessionService sessionService() {
        return new SessionService(sessionRepository());
    }

    @Bean
    public SessionRepository sessionRepository() {
        return new InMemorySessionRepository(clock());
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("game-sched-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    public GameCleanupService gameCleanupService(StompSessionSeatRegistry stompSessionSeatRegistry) {
        return new GameCleanupService(sessionRepository(), stompSessionSeatRegistry);
    }
}
