package org.kevinkib.cardgames.sessionmanagement.core.domain;

import org.kevinkib.cardgames.game.PlayerId;

import java.util.LinkedHashMap;
import java.util.Map;

public class SessionPlayer {

    private final PlayerId id;
    private SessionToken token;
    private boolean claimed;
    private boolean bot;
    private String name;
    private boolean rematchRequested;
    private boolean rematchDeparted;
    private boolean disconnected;
    private final Map<String, String> options = new LinkedHashMap<>();

    public SessionPlayer(PlayerId id, SessionToken token) {
        this.id = id;
        this.token = token;
        this.claimed = false;
        this.bot = false;
        this.name = null;
        this.rematchRequested = false;
    }

    public void claim(String name) {
        this.claimed = true;
        this.bot = false;
        this.name = (name == null || name.isBlank()) ? defaultName() : name.trim();
    }

    /** Claims the seat for a computer player; its token stays internal to the session. */
    void claimAsBot(String name, String defaultBotName) {
        this.claimed = true;
        this.bot = true;
        this.name = (name == null || name.isBlank()) ? defaultBotName : name.trim();
    }

    /**
     * Frees the seat; a seat that was held by a bot goes back to being an ordinary free seat. The seat gets a
     * fresh token, so the previous occupant's token never authenticates the next one.
     */
    void release() {
        this.claimed = false;
        this.bot = false;
        this.name = null;
        this.options.clear();
        this.rematchRequested = false;
        this.rematchDeparted = false;
        this.disconnected = false;
        this.token = SessionToken.generate();
    }

    /** The same occupant on another seat: token, name, bot flag, options and rematch flags move with it. */
    SessionPlayer movedTo(PlayerId newId) {
        SessionPlayer moved = new SessionPlayer(newId, token);
        moved.claimed = claimed;
        moved.bot = bot;
        moved.name = name;
        moved.rematchRequested = rematchRequested;
        moved.rematchDeparted = rematchDeparted;
        moved.disconnected = disconnected;
        moved.options.putAll(options);
        return moved;
    }

    void rename(String name) {
        this.name = name;
    }

    void setOption(String key, String value) {
        options.put(key, value);
    }

    void replaceOptions(Map<String, String> replacement) {
        options.clear();
        options.putAll(replacement);
    }

    /** The seat options, opaque to the session core (a computer seat's tier, for example). A copy. */
    public Map<String, String> options() {
        return Map.copyOf(options);
    }

    public String defaultName() {
        return "Player " + (id.id() + 1);
    }

    /** Asking for a rematch also cancels an earlier departure: the player is back and wants to play. */
    public void requestRematch() {
        this.rematchRequested = true;
        this.rematchDeparted = false;
    }

    public void clearRematch() {
        this.rematchRequested = false;
        this.rematchDeparted = false;
    }

    /** The player left for good (grace delay over, or quit): the others stop waiting for them. */
    void leaveRematch() {
        this.rematchDeparted = true;
        this.rematchRequested = false;
    }

    public boolean hasLeftRematch() {
        return rematchDeparted;
    }

    public boolean hasRequestedRematch() {
        return rematchRequested;
    }

    /** True while the human's connection is known to be down in a lobby (the host may remove the seat). */
    public boolean isDisconnected() {
        return disconnected;
    }

    void setDisconnected(boolean disconnected) {
        this.disconnected = disconnected;
    }

    public PlayerId id() {
        return id;
    }

    public SessionToken token() {
        return token;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public boolean isBot() {
        return bot;
    }

    public String name() {
        return name;
    }
}
