/**
 * BatailleCorse bounded context — game rules in {@code domain}, transport in {@code presentation}.
 *
 * <p>Builds on the shared kernel {@link org.kevinkib.cardgames.game}: the aggregate
 * {@code BatailleCorse} implements {@link org.kevinkib.cardgames.game.Game} and is keyed by
 * the shared {@link org.kevinkib.cardgames.game.GameId} and
 * {@link org.kevinkib.cardgames.game.PlayerId}; {@code BatailleCorseFactory} implements
 * {@link org.kevinkib.cardgames.game.GameFactory}. The generic {@code sessionmanagement}
 * context hosts this game purely through those kernel abstractions.
 */
package org.kevinkib.cardgames.bataillecorse;
