import { nextTick, readonly, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useCardAnimation } from './useCardAnimation';
import { useBatailleCorseStore } from '../state/BatailleCorse.store';

interface UseGameAnimationsOptions {
  playerDeckEl: () => HTMLImageElement | null | undefined;
  opponentDeckEl: () => HTMLImageElement | null | undefined;
  centerPileEl: () => HTMLImageElement | null | undefined;
  centerPileAreaEl: () => HTMLDivElement | null | undefined;
}

/**
 * Orchestrates every card animation in the game view: wraps `useCardAnimation`
 * and wires the store's optimistic action events (send/grab/slap/successful-slap/
 * erroneous-slap, plus the live pile-card swap) to it, calling
 * `notifyAnimationComplete()` at the same points as before so the store's event
 * queue stays in lockstep. Also owns the one-shot slap-impact "juice" on the
 * central card. The caller wires `cancel()` into onBeforeUnmount.
 */
export function useGameAnimations(opts: UseGameAnimationsOptions) {
  const store = useBatailleCorseStore();
  const { state: batailleCorse, myPlayerIndex, lastSend, lastGrab, lastSlap,
          lastSuccessfulSlap, lastErroneousSlap } = storeToRefs(store);

  const animation = useCardAnimation(
    opts.playerDeckEl,
    opts.opponentDeckEl,
    opts.centerPileEl,
    opts.centerPileAreaEl,
  );

  const {
    ghostCard, slapGhosts, isPileAnimating, isPileFlashing, frozenPileCard, cardDeltaIndicator,
  } = animation;

  // During send animation, the server responds within a few ms with the new card face.
  // Watch for that update and switch the ghost image immediately.
  watch(() => batailleCorse.value?.pile.cards.at(0), (newCard) => {
    animation.onNewPileCard(newCard);
  });

  // SEND is optimistic: topCard is snapshotted in the store at call time, no flush:'sync' needed.
  watch(lastSend, async (event) => {
    if (!event) return;
    const sourceEl = event.playerIndex === myPlayerIndex.value ? opts.playerDeckEl() : opts.opponentDeckEl();
    if (!sourceEl) return;
    await nextTick();
    const destRect = animation.getCenterPileRect();
    if (!destRect || destRect.width === 0) return;
    animation.animateSend(sourceEl.getBoundingClientRect(), destRect, event.topCard);
    // SEND is non-blocking in the queue — no notifyAnimationComplete needed.
  });

  // GRAB: pileCards snapshot is embedded in the event by the store.
  watch(lastGrab, async (event) => {
    if (!event) { animation.cancelPileAnimation(); store.notifyAnimationComplete(); return; }
    const { pileCards, winnerPlayerIndex } = event;
    await nextTick();
    const destEl = winnerPlayerIndex === myPlayerIndex.value ? opts.playerDeckEl() : opts.opponentDeckEl();
    const srcRect = animation.getCenterPileRect();
    if (!destEl || !srcRect || srcRect.width === 0) {
      animation.cancelPileAnimation();
      store.notifyAnimationComplete();
      return;
    }
    animation.showDeltaOnGrab(pileCards.length, destEl);
    await animation.animatePileToWinner(srcRect, destEl.getBoundingClientRect(), pileCards);
    store.notifyAnimationComplete();
  });

  // Immediate flash on any slap attempt, before the server responds.
  watch(lastSlap, () => animation.flashPile());

  // Slap "juice": a one-shot impact on the central card — a scale punch when the
  // slap lands, a red shake when it was a mistake. Re-armed via null→nextTick so
  // rapid repeat slaps replay the animation rather than no-op on the same class.
  const slapImpact = ref<'success' | 'error' | null>(null);
  let slapImpactTimer: ReturnType<typeof setTimeout> | null = null;
  function flashSlapImpact(kind: 'success' | 'error') {
    if (slapImpactTimer) clearTimeout(slapImpactTimer);
    slapImpact.value = null;
    void nextTick(() => {
      slapImpact.value = kind;
      slapImpactTimer = setTimeout(() => { slapImpact.value = null; }, 400);
    });
  }

  // Successful slap: pileCards snapshot is embedded in the event by the store.
  watch(lastSuccessfulSlap, async (event) => {
    if (!event) return;
    flashSlapImpact('success');
    const { pileCards, winnerPlayerIndex } = event;
    await nextTick();
    const destEl = winnerPlayerIndex === myPlayerIndex.value ? opts.playerDeckEl() : opts.opponentDeckEl();
    if (!destEl) { store.notifyAnimationComplete(); return; }
    const srcRect = animation.getCenterPileRect();
    if (!srcRect || srcRect.width === 0) { store.notifyAnimationComplete(); return; }
    animation.showDeltaOnSlap(pileCards.length, destEl);
    await animation.animatePileToWinner(srcRect, destEl.getBoundingClientRect(), pileCards);
    store.notifyAnimationComplete();
  });

  // Erroneous slap: animate 2 ghost cards from slapper's deck to center, show -2 indicator.
  watch(lastErroneousSlap, async (event) => {
    if (!event) return;
    flashSlapImpact('error');
    await nextTick();
    const srcEl = event.playerIndex === myPlayerIndex.value ? opts.playerDeckEl() : opts.opponentDeckEl();
    const destRect = animation.getCenterPileRect();
    if (!srcEl || !destRect) { store.notifyAnimationComplete(); return; }
    await animation.animateErroneousSlap(srcEl.getBoundingClientRect(), destRect);
    animation.showDeltaAlways(-2, srcEl);
    store.notifyAnimationComplete();
  });

  /** Cancel all in-flight animations and the pending slap-impact timer; wire into onBeforeUnmount. */
  function cancel() {
    if (slapImpactTimer) { clearTimeout(slapImpactTimer); slapImpactTimer = null; }
    animation.cancelAllAnimations();
  }

  return {
    ghostCard, slapGhosts, isPileAnimating, isPileFlashing, frozenPileCard, cardDeltaIndicator,
    slapImpact: readonly(slapImpact),
    cancel,
  };
}
