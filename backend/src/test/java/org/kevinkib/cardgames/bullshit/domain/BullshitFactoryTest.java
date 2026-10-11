package org.kevinkib.cardgames.bullshit.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.bot.ScriptedRandom;
import org.kevinkib.cardgames.bullshit.domain.claim.RankTarget;
import org.kevinkib.cardgames.bullshit.domain.claim.SuitTarget;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BullshitFactoryTest {

    @Test
    void givenId_whenCreate_thenReturnsPlayableBullshitWithThatId() {
        GameId id = GameId.generate();

        Game game = new BullshitFactory().create(id, 3);

        assertThat(game, instanceOf(Bullshit.class));
        assertThat(game.getId(), is(id));
        assertThat(game.getPlayerIds(), hasSize(3));
        assertThat(game.isFinished(), is(false));
    }

    @Test
    void gameType_isBullshit() {
        assertThat(new BullshitFactory().gameType(), is("bullshit"));
    }

    @Test
    void givenSuitOption_whenCreate_thenInitialTargetIsHeart() {
        Bullshit game = (Bullshit) new BullshitFactory(new ScriptedRandom(0.0))
                .create(GameId.generate(), 3, GameOptions.of(Map.of("claimMode", "suit")));

        assertThat(game.getCurrentTarget(), is(new SuitTarget(FrenchSuit.HEART)));
    }

    @Test
    void givenDeckSize32_whenCreate_thenShortDeckDealtAndRankCycleStartsAtSeven() {
        Bullshit game = (Bullshit) new BullshitFactory()
                .create(GameId.generate(), 2, GameOptions.of(Map.of("deckSize", "32")));

        assertThat(game.getPlayers().stream().mapToInt(p -> p.handSize()).sum(), is(32));
        assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.SEVEN)));
    }

    @Test
    void givenNoDeckSizeOption_whenCreate_thenShortDeck() {
        Bullshit game = (Bullshit) new BullshitFactory()
                .create(GameId.generate(), 2, GameOptions.of(Map.of("claimMode", "rank")));

        assertThat(game.getPlayers().stream().mapToInt(p -> p.handSize()).sum(), is(32));
    }

    @Test
    void givenNoOptions_whenCreate_thenInitialTargetIsAce() {
        Bullshit game = (Bullshit) new BullshitFactory().create(GameId.generate(), 3);

        assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.ACE)));
    }

    @Test
    void optionDescriptors_declareClaimModeAndDeckSize() {
        assertThat(new BullshitFactory().optionDescriptors(), is(List.of(
                new OptionDescriptor("claimMode", OptionType.CHOICE, List.of("rank", "suit"), "rank",
                        "options.bullshit.claimMode"),
                new OptionDescriptor("deckSize", OptionType.CHOICE, List.of("52", "32"), "32",
                        "options.bullshit.deckSize"))));
    }

    @Test
    void everyDescriptorDefault_isOneOfItsValues() {
        for (OptionDescriptor descriptor : new BullshitFactory().optionDescriptors()) {
            assertThat(descriptor.values().contains(descriptor.defaultValue()), is(true));
        }
    }

    @Test
    void givenNoOptions_whenValidate_thenDefaultsFilled() {
        assertThat(new BullshitFactory().validate(GameOptions.none()).values(),
                is(Map.of("claimMode", "rank", "deckSize", "32")));
    }

    @Test
    void givenValidOptions_whenValidate_thenKeptAndCompleted() {
        assertThat(new BullshitFactory().validate(GameOptions.of(Map.of("claimMode", "suit"))).values(),
                is(Map.of("claimMode", "suit", "deckSize", "32")));
    }

    @Test
    void givenUnknownKeyOrValue_whenValidate_thenRefused() {
        BullshitFactory factory = new BullshitFactory();
        assertThrows(InvalidOptionException.class, () -> factory.validate(GameOptions.of(Map.of("bogus", "x"))));
        assertThrows(InvalidOptionException.class,
                () -> factory.validate(GameOptions.of(Map.of("claimMode", "bogus"))));
        assertThrows(InvalidOptionException.class,
                () -> factory.validate(GameOptions.of(Map.of("deckSize", "40"))));
    }
}
