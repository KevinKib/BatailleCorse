package org.kevinkib.cardgames.bullshit.domain.options;

import org.kevinkib.cardgames.bullshit.domain.claim.ClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimModeOption;
import org.kevinkib.cardgames.bullshit.domain.deck.DeckSize;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.InvalidOptionException;

import java.util.Map;
import java.util.random.RandomGenerator;

/** Typed Bullshit creation options, parsed once from the opaque {@link GameOptions} map at the
 *  Bullshit edge. Future options extend this record and its {@link #from} parsing. */
public record BullshitOptions(ClaimModeOption claimMode, DeckSize deckSize) {

    public static final String CLAIM_MODE_KEY = "claimMode";
    public static final String DECK_SIZE_KEY = "deckSize";

    public BullshitOptions(ClaimModeOption claimMode) {
        this(claimMode, DeckSize.DEFAULT);
    }

    public static BullshitOptions from(GameOptions options) {
        return new BullshitOptions(
                ClaimModeOption.fromKey(options.get(CLAIM_MODE_KEY).orElse(null)),
                DeckSize.fromKey(options.get(DECK_SIZE_KEY).orElse(null)));
    }

    /**
     * Strict parse for host edits: an unknown key or value is refused instead of falling back to a
     * default; a missing key takes its default. {@link #from} stays lenient for room creation.
     *
     * @throws InvalidOptionException on an unknown key or value
     */
    public static BullshitOptions parseStrict(GameOptions options) {
        for (String key : options.values().keySet()) {
            if (!CLAIM_MODE_KEY.equals(key) && !DECK_SIZE_KEY.equals(key)) {
                throw new InvalidOptionException("Unknown option " + key);
            }
        }
        ClaimModeOption claimMode = options.get(CLAIM_MODE_KEY)
                .map(value -> ClaimModeOption.find(value).orElseThrow(() -> invalidValue(CLAIM_MODE_KEY, value)))
                .orElse(ClaimModeOption.DEFAULT);
        DeckSize deckSize = options.get(DECK_SIZE_KEY)
                .map(value -> DeckSize.find(value).orElseThrow(() -> invalidValue(DECK_SIZE_KEY, value)))
                .orElse(DeckSize.DEFAULT);
        return new BullshitOptions(claimMode, deckSize);
    }

    private static InvalidOptionException invalidValue(String key, String value) {
        return new InvalidOptionException("Invalid value " + value + " for option " + key);
    }

    /** The full option map, every key set, as the lobby stores and shows it. */
    public GameOptions toGameOptions() {
        return GameOptions.of(Map.of(CLAIM_MODE_KEY, claimMode.key(), DECK_SIZE_KEY, deckSize.key()));
    }

    public ClaimMode toClaimMode(RandomGenerator random) {
        return claimMode.create(deckSize, random);
    }
}
