import { computed, onBeforeUnmount, onMounted, ref, type Ref } from 'vue';

export interface HandLayoutOptions {
  /** Widest a card may be (px). */
  maxCard: number;
  /** Narrowest a card may shrink to when the hand is crowded (px). */
  minCard: number;
  /** Space between cards when nothing overlaps (px). */
  gap?: number;
  /** Horizontal distance between card left edges we try to keep before shrinking cards (px). */
  comfortStep?: number;
}

export interface HandLayout {
  cardWidth: number;
  /** Distance between the left edges of two neighbouring cards (px). */
  step: number;
}

/**
 * Lays `count` cards on ONE row inside `availableWidth`: full-size and spaced when they fit,
 * otherwise overlapped. When the overlap would hide too much of each card, the cards shrink
 * (down to `minCard`) before the overlap grows further.
 */
export function computeHandLayout(count: number, availableWidth: number, opts: HandLayoutOptions): HandLayout {
  const gap = opts.gap ?? 4;
  const comfortStep = opts.comfortStep ?? 22;
  const maxCard = Math.max(opts.minCard, opts.maxCard);
  const spaced = { cardWidth: maxCard, step: maxCard + gap };
  if (count <= 1 || availableWidth <= 0) return spaced;
  if (count * maxCard + (count - 1) * gap <= availableWidth) return spaced;

  let cardWidth = maxCard;
  let step = (availableWidth - cardWidth) / (count - 1);
  if (step < comfortStep) {
    cardWidth = Math.min(maxCard, Math.max(opts.minCard, availableWidth - (count - 1) * comfortStep));
    step = (availableWidth - cardWidth) / (count - 1);
  }
  return { cardWidth: Math.floor(cardWidth), step: Math.min(step, cardWidth + gap) };
}

const MAX_CARD_PX = 76;
const MIN_CARD_PX = 44;
const HEIGHT_SHARE = 0.09;

/** Biggest card the viewport height allows: the hand must leave room for the table above it. */
export function maxCardForViewport(viewportHeight: number): number {
  return Math.min(MAX_CARD_PX, Math.max(MIN_CARD_PX, Math.round(viewportHeight * HEIGHT_SHARE)));
}

/** Tracks the width of `container` and the window height, and derives the hand layout from them. */
export function useHandLayout(container: Ref<HTMLElement | null>, count: () => number) {
  const width = ref(0);
  const viewportHeight = ref(typeof window === 'undefined' ? 800 : window.innerHeight);
  let observer: ResizeObserver | null = null;

  function measure() {
    width.value = container.value?.clientWidth ?? 0;
    viewportHeight.value = window.innerHeight;
  }

  onMounted(() => {
    measure();
    if (typeof ResizeObserver !== 'undefined' && container.value) {
      observer = new ResizeObserver(measure);
      observer.observe(container.value);
    }
    window.addEventListener('resize', measure);
  });
  onBeforeUnmount(() => {
    observer?.disconnect();
    window.removeEventListener('resize', measure);
  });

  const layout = computed(() => computeHandLayout(count(), width.value, {
    maxCard: maxCardForViewport(viewportHeight.value),
    minCard: MIN_CARD_PX,
  }));
  return { layout };
}
