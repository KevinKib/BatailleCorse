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
  /** 1, or 2 when a single row would overlap the cards too much. */
  rows: 1 | 2;
  /** Cards on the first row (the second holds the rest). */
  perRow: number;
}

/** Share of the card width kept for the two-row layout, so both rows fit the height budget. */
const TWO_ROW_CARD_SHARE = 0.85;

/** One row: full-size and spaced when it fits, else overlapped, shrinking cards (down to `minCard`) before the overlap grows. */
function layRow(count: number, availableWidth: number, maxCard: number, minCard: number, gap: number, comfortStep: number) {
  if (count <= 1 || availableWidth <= 0) return { cardWidth: maxCard, step: maxCard + gap };
  if (count * maxCard + (count - 1) * gap <= availableWidth) return { cardWidth: maxCard, step: maxCard + gap };
  let cardWidth = maxCard;
  let step = (availableWidth - cardWidth) / (count - 1);
  if (step < comfortStep) {
    cardWidth = Math.min(maxCard, Math.max(minCard, availableWidth - (count - 1) * comfortStep));
    step = (availableWidth - cardWidth) / (count - 1);
  }
  return { cardWidth: Math.floor(cardWidth), step: Math.min(step, cardWidth + gap) };
}

/**
 * Lays `count` cards inside `availableWidth`. On one row while every card keeps at least
 * `comfortStep` px visible (cards shrink to `minCard` first); otherwise on two balanced rows,
 * the first holding the extra card of an odd hand. The two-row layout overlaps as much as
 * needed (never less than the width allows) so 52 cards still fit a phone.
 */
export function computeHandLayout(count: number, availableWidth: number, opts: HandLayoutOptions): HandLayout {
  const gap = opts.gap ?? 4;
  const comfortStep = opts.comfortStep ?? 22;
  const maxCard = Math.max(opts.minCard, opts.maxCard);
  const single = (rows: 1) => ({ ...layRow(count, availableWidth, maxCard, opts.minCard, gap, comfortStep), rows, perRow: count });
  if (count <= 1 || availableWidth <= 0) return single(1);
  if ((availableWidth - opts.minCard) / (count - 1) >= comfortStep || count * maxCard + (count - 1) * gap <= availableWidth) {
    return single(1);
  }
  const perRow = Math.ceil(count / 2);
  const twoRowMax = Math.max(opts.minCard, Math.round(maxCard * TWO_ROW_CARD_SHARE));
  return { ...layRow(perRow, availableWidth, twoRowMax, opts.minCard, gap, comfortStep), rows: 2, perRow };
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
