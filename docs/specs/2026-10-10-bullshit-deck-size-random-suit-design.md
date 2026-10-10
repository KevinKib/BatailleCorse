# Bullshit: deck size option and random claimed suit (issues #98, #105)

## Context

Playtests showed that 52 cards for two players is excessive (#98) and that the suit-mode cycle
(heart, diamond, club, spade) is too predictable across rounds (#105). Both are product decisions;
neither is described by the standard "Cheat" / "I Doubt It" rules.

## Decisions

1. **Deck size option.** New creation option `deckSize` with values `32` (7 to Ace) and `52`.
   The payload field is optional (backward compatible) and an absent or unknown value means `52`.
   The create screen preselects `32`. The option lives in `bullshit/domain` (`DeckSize`,
   `BullshitOptions`), is forwarded as an opaque `GameOptions` key, and is exposed in the state DTO
   as `deckSize`.
2. **Rank cycle follows the deck.** With 52 cards the rank mode keeps A, 2, ..., K. With 32 cards
   it skips absent ranks and cycles 7, 8, 9, 10, J, Q, K, A, starting at 7.
3. **Everything that assumes a full deck uses the real one.** Dealing, and `BotMemory` (unseen
   card population), take the game's `DeckSize`. `PlausibilityEstimator` and `BotTuning` only
   work from counts supplied by the memory, so they need no change.
4. **Random suit per round.** A round ends each time a Bullshit call resolves (liar or not). Suit
   mode draws the first suit at random, and draws a new starting suit at the end of every round
   (the same suit may repeat). Within a round the suit follows the fixed cycle from the starting
   suit. Rank mode is unaffected.
5. **Randomness is a port.** `ClaimMode` gets `nextRound(current)` (default: returns `current`,
   so rank mode is untouched). `CyclingSuitClaimMode` takes a `java.util.random.RandomGenerator`
   (the same port the bots use), injected by `BullshitFactory` from `AppConfig`, and deterministic
   in tests.

## Out of scope

Lobby display of the deck size beyond what the state DTO carries; bot strategy changes (the
look-ahead in `turnsUntilNeeded` keeps assuming no challenge, which is a heuristic anyway).
