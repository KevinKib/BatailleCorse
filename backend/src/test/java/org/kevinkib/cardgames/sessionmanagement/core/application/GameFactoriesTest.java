package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameFactoriesTest {

    private final GameFactories factories =
            new GameFactories(List.of(new BatailleCorseFactory(), new BullshitFactory()));

    @Test
    void givenBullshitSlug_whenFactoryFor_thenCreatesBullshit() {
        assertThat(factories.factoryFor("bullshit").create(GameId.generate(), 3),
                instanceOf(Bullshit.class));
    }

    @Test
    void givenBatailleCorseSlug_whenFactoryFor_thenCreatesBatailleCorse() {
        assertThat(factories.factoryFor("bataille-corse").create(GameId.generate(), 2),
                instanceOf(BatailleCorse.class));
    }

    @Test
    void givenUnknownSlug_whenFactoryFor_thenThrows() {
        assertThrows(IllegalArgumentException.class, () -> factories.factoryFor("chess"));
    }

    @Test
    void givenBullshit_whenLookupBounds_thenTwoToSix() {
        assertThat(factories.minPlayers("bullshit"), is(2));
        assertThat(factories.maxPlayers("bullshit"), is(6));
    }

    @Test
    void givenBatailleCorse_whenLookupBounds_thenTwoToTwo() {
        assertThat(factories.minPlayers("bataille-corse"), is(2));
        assertThat(factories.maxPlayers("bataille-corse"), is(2));
    }

    @Test
    void givenFactoryWithoutOptionsOverride_whenCreateWithOptions_thenDelegatesToTwoArg() {
        GameFactories factories = new GameFactories(java.util.List.of(new BatailleCorseFactory()));

        var game = factories.factoryFor("bataille-corse")
                .create(GameId.generate(), 2, org.kevinkib.cardgames.game.GameOptions.none());

        assertThat(game, instanceOf(org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse.class));
    }

    @Test
    void givenBullshit_whenOptionDescriptors_thenClaimModeAndDeckSize() {
        assertThat(factories.optionDescriptors("bullshit").stream().map(d -> d.key()).toList(),
                is(List.of("claimMode", "deckSize")));
    }

    @Test
    void givenBatailleCorseAndKobo_whenOptionDescriptors_thenNone() {
        GameFactories all = new GameFactories(
                List.of(new BatailleCorseFactory(), new BullshitFactory(), new KoboFactory()));
        assertThat(all.optionDescriptors("bataille-corse"), is(empty()));
        assertThat(all.optionDescriptors("kobo"), is(empty()));
    }

    @Test
    void givenBatailleCorseAndKobo_whenValidateAnyKey_thenRefused() {
        GameFactories all = new GameFactories(
                List.of(new BatailleCorseFactory(), new BullshitFactory(), new KoboFactory()));
        GameOptions anything = GameOptions.of(Map.of("whatever", "x"));
        assertThrows(InvalidOptionException.class, () -> all.validate("bataille-corse", anything));
        assertThrows(InvalidOptionException.class, () -> all.validate("kobo", anything));
        assertThat(all.validate("kobo", GameOptions.none()), is(GameOptions.none()));
    }

    @Test
    void givenBullshit_whenValidate_thenDelegatesToTheFactory() {
        assertThat(factories.validate("bullshit", GameOptions.of(Map.of("claimMode", "suit"))).values(),
                is(Map.of("claimMode", "suit", "deckSize", "32")));
        assertThrows(InvalidOptionException.class,
                () -> factories.validate("bullshit", GameOptions.of(Map.of("claimMode", "bogus"))));
    }
}
