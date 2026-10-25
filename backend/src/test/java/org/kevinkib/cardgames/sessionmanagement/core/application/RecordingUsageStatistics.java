package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.sessionmanagement.core.application.port.UsageStatistics;

import java.util.ArrayList;
import java.util.List;

/** Test double: records every event as a short string, e.g. {@code created:tiered} or {@code started:tiered:2h1b}. */
public class RecordingUsageStatistics implements UsageStatistics {

    public final List<String> events = new ArrayList<>();

    @Override
    public void gameCreated(String gameType) {
        events.add("created:" + gameType);
    }

    @Override
    public void gameStarted(String gameType, int humans, int bots) {
        events.add("started:" + gameType + ":" + humans + "h" + bots + "b");
    }

    @Override
    public void gameFinished(String gameType) {
        events.add("finished:" + gameType);
    }

    @Override
    public void rematch(String gameType) {
        events.add("rematch:" + gameType);
    }

    @Override
    public void seatJoined(String gameType) {
        events.add("joined:" + gameType);
    }

    @Override
    public void botAdded(String gameType) {
        events.add("bot:" + gameType);
    }
}
