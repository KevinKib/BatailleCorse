package org.kevinkib.cardgames.sessionmanagement.core.application;

import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.OptionDescriptor;
import org.kevinkib.cardgames.game.OptionType;

import java.util.List;

/** Test game that declares one bot seat option, {@code tier}, so the session core can be exercised with it. */
public class TieredFactory extends BullshitFactory {

    public static final String GAME_TYPE = "tiered";

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public List<OptionDescriptor> seatOptionDescriptors() {
        return List.of(new OptionDescriptor("tier", OptionType.CHOICE, List.of("0", "1", "2"), "1", "seat.tier"));
    }
}
