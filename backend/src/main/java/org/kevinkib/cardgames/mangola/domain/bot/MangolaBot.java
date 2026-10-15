package org.kevinkib.cardgames.mangola.domain.bot;

import org.kevinkib.cardgames.mangola.domain.MangolaPosition;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Side;

import java.util.Collection;
import java.util.List;

/**
 * The strategy of a computer seat. It reasons on its own information only: the layout decision never sees
 * the opponent's draft (placement is hidden), and the move decision sees the board, which both players see.
 * Every tie is broken through an injected random generator, so a strategy is deterministic with a constant
 * one.
 */
public interface MangolaBot {

    /**
     * The 16 counts (in the order of the side's loop) the seat arranges during the placement phase: they sum to
     * 32 and are never one of the {@code rejected} layouts, which the game refused at the confirmation because
     * they left the starting seat without a move. Side A starts, so the side tells whether the bot starts.
     */
    int[] decideLayout(MangolaRules rules, Side side, Collection<List<Integer>> rejected);

    /**
     * The pit to sow for the side to move of the position.
     *
     * @throws IllegalStateException when the side has no legal pit
     */
    int decideMove(MangolaPosition position, Side side);
}
