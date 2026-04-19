import { nextTick, reactive, readonly, ref } from 'vue';
import cardBackUrl from '/src/resources/cards/png/card_back.png?inline';
import type Card from '../service/model/Card';

const SLAP_DURATION = 280;
const SLAP_STAGGER = 60;
const SLAP_OFFSETS = [{ x: 0, y: 0 }, { x: 6, y: -4 }, { x: -5, y: -7 }];
const ERRONEOUS_SLAP_DURATION = 220;
const ERRONEOUS_SLAP_STAGGER = 50;

const cardImages = import.meta.glob('/src/resources/cards/png/*.png', { eager: true, query: '?inline' });

// Kept at module scope so decoded bitmaps are not garbage-collected during the session.
const _preloadedImages: HTMLImageElement[] = [];

export function preloadAllCards(): void {
  for (const key in cardImages) {
    const src = (cardImages[key] as { default: string }).default;
    const img = new Image();
    _preloadedImages.push(img);
    img.src = src;
    img.onload = () => {
      if ('decode' in img) img.decode().catch(() => {});
    };
    img.onerror = () => {};
  }
}

function getCardUrl(rank: string, suit: string): string {
  const key = `/src/resources/cards/png/card_${rank.toLowerCase()}_${suit.toLowerCase()}.png`;
  return (cardImages[key] as { default: string })?.default ?? cardBackUrl;
}

function startTransition(
  ghost: { transitioning: boolean; x: number; y: number; width: number; height: number },
  destRect: DOMRect,
) {
  nextTick(() => {
    // Force a synchronous reflow so the browser has painted the initial position
    // before we apply the transition. More reliable than double-RAF on throttled tabs.
    void document.body.getBoundingClientRect();
    ghost.transitioning = true;
    ghost.x = destRect.left;
    ghost.y = destRect.top;
    ghost.width = destRect.width;
    ghost.height = destRect.height;
  });
}

export function useCardAnimation(
  getPlayerDeckEl: () => HTMLImageElement | null | undefined,
  getOpponentDeckEl: () => HTMLImageElement | null | undefined,
  getCenterPileEl: () => HTMLImageElement | null | undefined,
  getCenterPileAreaEl: () => HTMLDivElement | null | undefined,
) {
  const ghostCard = reactive({
    visible: false,
    transitioning: false,
    x: 0, y: 0, width: 0, height: 0,
    duration: 100,
    src: cardBackUrl,
  });

  const isPileAnimating = ref(false);
  const isPileFlashing = ref(false);
  const frozenPileCard = reactive({ rank: '', suit: '' });

  const slapGhosts = SLAP_OFFSETS.map(() =>
    reactive({ visible: false, transitioning: false, x: 0, y: 0, width: 0, height: 0, src: cardBackUrl, duration: SLAP_DURATION })
  );

  const cardDeltaIndicator = reactive({ visible: false, delta: 0, x: 0, y: 0 });
  let recentSlapIndicatorShown = false;

  let ghostCardTimeoutId: ReturnType<typeof setTimeout> | null = null;

  // --- Internal helpers ---

  function getCenterPileRect(): DOMRect | undefined {
    const el = getCenterPileEl();
    return (el && el.offsetParent !== null)
      ? el.getBoundingClientRect()
      : getCenterPileAreaEl()?.getBoundingClientRect();
  }

  function showCardDelta(delta: number, deckEl: HTMLImageElement | null | undefined) {
    if (delta === 0 || !deckEl) return;
    const rect = deckEl.getBoundingClientRect();
    cardDeltaIndicator.visible = false;
    nextTick(() => {
      cardDeltaIndicator.delta = delta;
      cardDeltaIndicator.x = rect.right + 8;
      cardDeltaIndicator.y = rect.top + rect.height / 2 - 16;
      cardDeltaIndicator.visible = true;
      setTimeout(() => { cardDeltaIndicator.visible = false; }, 1400);
    });
  }

  function animateGhostCard(srcRect: DOMRect, destRect: DOMRect, duration: number, src = cardBackUrl) {
    if (ghostCardTimeoutId !== null) {
      clearTimeout(ghostCardTimeoutId);
      ghostCardTimeoutId = null;
    }
    ghostCard.visible = false;
    ghostCard.transitioning = false;
    ghostCard.src = src;
    ghostCard.x = srcRect.left;
    ghostCard.y = srcRect.top;
    ghostCard.width = srcRect.width;
    ghostCard.height = srcRect.height;
    ghostCard.duration = duration;
    ghostCard.visible = true;

    startTransition(ghostCard, destRect);

    ghostCardTimeoutId = setTimeout(() => {
      ghostCardTimeoutId = null;
      ghostCard.visible = false;
      ghostCard.transitioning = false;
      isPileAnimating.value = false;
    }, duration + 50);
  }

  function animateGhostCards(srcRect: DOMRect, destRect: DOMRect, pileCards: Card[], onComplete?: () => void) {
    const ghostCount = Math.min(pileCards.length, slapGhosts.length);
    slapGhosts.slice(0, ghostCount).forEach((ghost, i) => {
      const offset = SLAP_OFFSETS[i];
      setTimeout(() => {
        const card = pileCards[i];
        ghost.src = card ? getCardUrl(card.rank, card.suit) : cardBackUrl;
        ghost.duration = SLAP_DURATION;
        ghost.visible = false;
        ghost.transitioning = false;
        ghost.x = srcRect.left + offset.x;
        ghost.y = srcRect.top + offset.y;
        ghost.width = srcRect.width;
        ghost.height = srcRect.height;
        ghost.visible = true;

        startTransition(ghost, destRect);

        setTimeout(() => {
          ghost.visible = false;
          ghost.transitioning = false;
          if (i === ghostCount - 1) onComplete?.();
        }, SLAP_DURATION + 50);
      }, i * SLAP_STAGGER);
    });
  }

  // --- Public API ---

  function onNewPileCard(card: Card | undefined) {
    if (isPileAnimating.value && card) {
      const src = getCardUrl(card.rank, card.suit);
      ghostCard.src = src;
    }
  }

  function flashPile() {
    isPileFlashing.value = true;
    setTimeout(() => { isPileFlashing.value = false; }, 400);
  }

  /** Animate a card being sent from a player's deck to the center pile. */
  function animateSend(srcRect: DOMRect, destRect: DOMRect, topCard: Card | undefined) {
    frozenPileCard.rank = topCard?.rank ?? '';
    frozenPileCard.suit = topCard?.suit ?? '';
    isPileAnimating.value = true;
    animateGhostCard(srcRect, destRect, 100);
  }

  /** Animate pile cards flying to a winner's deck (slap or grab). */
  function animatePileToWinner(srcRect: DOMRect, destRect: DOMRect, pileCards: Card[]) {
    isPileAnimating.value = true;
    frozenPileCard.rank = '';
    frozenPileCard.suit = '';
    animateGhostCards(srcRect, destRect, pileCards, () => {
      isPileAnimating.value = false;
    });
  }

  /** Reset pile animation state when an animation cannot start (missing rects, etc.). */
  function cancelPileAnimation() {
    isPileAnimating.value = false;
  }

  /** Cancel all in-flight animations and reset visible state — call on component unmount. */
  function cancelAllAnimations() {
    if (ghostCardTimeoutId !== null) {
      clearTimeout(ghostCardTimeoutId);
      ghostCardTimeoutId = null;
    }
    ghostCard.visible = false;
    ghostCard.transitioning = false;
    slapGhosts.forEach(g => { g.visible = false; g.transitioning = false; });
    isPileAnimating.value = false;
    isPileFlashing.value = false;
    cardDeltaIndicator.visible = false;
    recentSlapIndicatorShown = false;
  }

  /** Animate 2 penalty cards from a player's deck to the center pile. */
  function animateErroneousSlap(srcRect: DOMRect, destRect: DOMRect) {
    [slapGhosts[0], slapGhosts[1]].forEach((ghost, i) => {
      setTimeout(() => {
        ghost.src = cardBackUrl;
        ghost.duration = ERRONEOUS_SLAP_DURATION;
        ghost.visible = false;
        ghost.transitioning = false;
        ghost.x = srcRect.left;
        ghost.y = srcRect.top;
        ghost.width = srcRect.width;
        ghost.height = srcRect.height;
        ghost.visible = true;

        startTransition(ghost, destRect);

        setTimeout(() => {
          ghost.visible = false;
          ghost.transitioning = false;
        }, ERRONEOUS_SLAP_DURATION + 50);
      }, i * ERRONEOUS_SLAP_STAGGER);
    });
  }

  /** Show a card delta indicator and mark it so the grab event won't show a duplicate. */
  function showDeltaOnSlap(count: number, deckEl: HTMLImageElement | null | undefined) {
    recentSlapIndicatorShown = true;
    setTimeout(() => { recentSlapIndicatorShown = false; }, 2000);
    showCardDelta(count, deckEl);
  }

  /** Show a card delta indicator only if no slap indicator was recently shown. */
  function showDeltaOnGrab(count: number, deckEl: HTMLImageElement | null | undefined) {
    if (!recentSlapIndicatorShown) showCardDelta(count, deckEl);
  }

  /** Show a card delta indicator unconditionally (e.g. erroneous slap penalty). */
  function showDeltaAlways(delta: number, deckEl: HTMLImageElement | null | undefined) {
    showCardDelta(delta, deckEl);
  }

  return {
    // Template-bound reactive state (readonly — composable owns all mutations)
    ghostCard,
    slapGhosts,
    isPileAnimating: readonly(isPileAnimating),
    isPileFlashing: readonly(isPileFlashing),
    frozenPileCard: readonly(frozenPileCard),
    cardDeltaIndicator,
    // Functions for view watchers
    getCenterPileRect,
    onNewPileCard,
    flashPile,
    animateSend,
    animatePileToWinner,
    cancelPileAnimation,
    cancelAllAnimations,
    animateErroneousSlap,
    showDeltaOnSlap,
    showDeltaOnGrab,
    showDeltaAlways,
  };
}
