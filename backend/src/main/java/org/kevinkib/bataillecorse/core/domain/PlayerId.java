package org.kevinkib.bataillecorse.core.domain;

public record PlayerId(Integer id) {

    @Override
    public String toString() {
        return id.toString();
    }
}
