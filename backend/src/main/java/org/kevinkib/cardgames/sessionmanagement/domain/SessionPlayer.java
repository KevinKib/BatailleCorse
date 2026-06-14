package org.kevinkib.cardgames.sessionmanagement.domain;

import org.kevinkib.cardgames.game.PlayerId;

public class SessionPlayer {

    private final PlayerId id;
    private final SessionToken token;
    private boolean claimed;
    private String name;
    private boolean rematchRequested;

    public SessionPlayer(PlayerId id, SessionToken token) {
        this.id = id;
        this.token = token;
        this.claimed = false;
        this.name = null;
        this.rematchRequested = false;
    }

    public void claim(String name) {
        this.claimed = true;
        this.name = name;
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

    public String name() {
        return name;
    }
}
