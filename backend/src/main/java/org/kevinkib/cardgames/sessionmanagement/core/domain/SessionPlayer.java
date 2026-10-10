package org.kevinkib.cardgames.sessionmanagement.core.domain;

import org.kevinkib.cardgames.game.PlayerId;

public class SessionPlayer {

    private final PlayerId id;
    private final SessionToken token;
    private boolean claimed;
    private boolean bot;
    private String name;
    private boolean rematchRequested;

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

    /** Frees the seat; a seat that was held by a bot goes back to being an ordinary free seat. */
    void release() {
        this.claimed = false;
        this.bot = false;
        this.name = null;
        this.rematchRequested = false;
    }

    void rename(String name) {
        this.name = name;
    }

    public String defaultName() {
        return "Player " + (id.id() + 1);
    }

    public void requestRematch() {
        this.rematchRequested = true;
    }

    public void clearRematch() {
        this.rematchRequested = false;
    }

    public boolean hasRequestedRematch() {
        return rematchRequested;
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
