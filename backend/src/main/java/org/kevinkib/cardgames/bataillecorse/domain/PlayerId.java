package org.kevinkib.cardgames.bataillecorse.domain;

public record PlayerId(Integer id) {

    @Override
    public String toString() {
        return id.toString();
    }
}
