/**
 * President bounded context (Le Président, Trou du cul): a climbing game for 3 to 8 players.
 *
 * <p>{@code domain} holds the rules: the {@code President} aggregate, built on the shared kernel
 * {@link org.kevinkib.cardgames.game} and FrenchCards only. It knows neither sessions, tokens nor
 * transport. The aggregate sits at the root of {@code domain}; its concepts live in sub-packages
 * ({@code rules}, {@code card}, {@code play}, {@code trick}, {@code round}, {@code exchange},
 * {@code event}), each with the exceptions it protects. Presentation arrives with a later PR.
 */
package org.kevinkib.cardgames.president;
