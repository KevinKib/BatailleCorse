package org.kevinkib.bataillecorse.sessionmanagement.domain;

import org.kevinkib.bataillecorse.core.domain.PlayerId;

public class SessionPlayer {

    private final PlayerId id;
    private final SessionToken token;
    private boolean claimed;
    private String name;

    public SessionPlayer(PlayerId id, SessionToken token) {
        this.id = id;
        this.token = token;
        this.claimed = false;
        this.name = null;
    }

    public void claim(String name) {
        this.claimed = true;
        this.name = name;
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
