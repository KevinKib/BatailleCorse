package org.kevinkib.cardgames.kilodemerde.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloCards.c;
import static org.kevinkib.cardgames.kilodemerde.domain.KiloCards.cards;

class KiloValueTest {

    @ParameterizedTest
    @CsvSource({"AS,100", "KH,13", "QD,12", "JC,11", "10S,10", "9S,9", "8H,8", "6D,6", "2C,2", "7S,0"})
    void valuesFollowTheDucaleScale(String code, int kilos) {
        assertEquals(kilos, KiloValue.of(c(code)));
    }

    @Test
    void onlyTheSevenIsTheFlush() {
        assertTrue(KiloValue.isFlush(c("7D")));
        assertFalse(KiloValue.isFlush(c("8D")));
        assertFalse(KiloValue.isFlush(c("AS")));
    }

    @Test
    void totalAddsTheValues() {
        assertEquals(113, KiloValue.total(cards("AH KS")));
        assertEquals(0, KiloValue.total(cards("")));
    }
}
