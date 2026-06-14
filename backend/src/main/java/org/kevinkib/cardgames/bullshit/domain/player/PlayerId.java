package org.kevinkib.cardgames.bullshit.domain.player;

public record PlayerId(Integer id) {

    @Override
    public String toString() {
        return id.toString();
    }
}
