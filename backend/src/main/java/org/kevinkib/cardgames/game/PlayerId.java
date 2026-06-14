package org.kevinkib.cardgames.game;

public record PlayerId(Integer id) {

    @Override
    public String toString() {
        return id.toString();
    }
}
