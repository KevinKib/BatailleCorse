<script setup lang="ts">
import { computed } from 'vue';
import { Button } from 'primevue';
import type { PresidentPlayer, PresidentRound, PresidentTitle } from '../../model/president/PresidentState';
import { seatsByPlace, titleKey, titleOrder } from '../../model/president/titles';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import { PRESIDENT_TEMPO } from '../../model/president/tempo';
import type { PresidentTitleKey } from '../../locales/Messages';

// The end-of-round sheet: the finishing order with the title each seat has just earned, how it moved from the title
// held this round, the points of the round and the running total, who is ready for the next one, and the way on.
// Every figure is the server's (`lastRound`); the arrows only compare two titles.
const props = defineProps<{
  players: PresidentPlayer[];
  round: number;
  lastRound?: PresidentRound | null;
  nameOf: (seat: number) => string;
  /** False once I pressed Ready: the button gives way to a waiting line. */
  canReady: boolean;
  /** The match ends with this round: the way on is the final scores, not the next round. */
  final?: boolean;
  /** Instant or reduced motion: the sheet and its places come in at once. */
  still?: boolean;
  /** The speed factor of the animations (1 normal, 0.5 fast). */
  factor?: number;
}>();
const emit = defineEmits<{ ready: []; continue: [] }>();

const ui = useI18n().presidentUi;
// The places come in one by one, best first, once the sheet is up (each row's delay is its own CSS variable).
const placeDelay = (index: number) =>
  props.still ? 0 : Math.round(index * PRESIDENT_TEMPO.roundEnd.placeStaggerMs * (props.factor ?? 1));
const slideMs = computed(() => props.still ? 0 : Math.round(PRESIDENT_TEMPO.roundEnd.slideMs * (props.factor ?? 1)));
const titleText = (title: PresidentTitle | null) => title ? ui.titles[titleKey(title) as PresidentTitleKey] : '';

interface Row {
  seat: number;
  place: number | null;
  title: PresidentTitle | null;
  points: number | null;
  total: number | null;
  change: { direction: 'up' | 'down' | 'same'; text: string } | null;
  ready: boolean;
  forbidden: boolean;
}

// A seat's own `title` is the one it played this round with (null in round 1); the one earned is in `lastRound`.
function changeOf(before: PresidentTitle | null, after: PresidentTitle | null): Row['change'] {
  if (before === null || after === null) return null;
  const from = titleText(before);
  if (before === after) return { direction: 'same', text: ui.summary.same };
  return titleOrder(after) < titleOrder(before)
    ? { direction: 'up', text: format(ui.summary.up, { title: from }) }
    : { direction: 'down', text: format(ui.summary.down, { title: from }) };
}

const rows = computed<Row[]>(() => {
  const byseat = new Map(props.players.map(p => [p.seat, p]));
  if (props.lastRound) {
    return [...props.lastRound.players].sort((a, b) => a.place - b.place || a.seat - b.seat).map(r => ({
      seat: r.seat, place: r.place, title: r.title, points: r.points, total: r.total,
      change: changeOf(byseat.get(r.seat)?.title ?? null, r.title),
      ready: byseat.get(r.seat)?.ready ?? false,
      forbidden: r.forbiddenFinish,
    }));
  }
  return seatsByPlace(props.players).map(p => ({
    seat: p.seat, place: p.place, title: p.title, points: null, total: null, change: null, ready: p.ready,
    forbidden: p.forbiddenFinish,
  }));
});
const ICONS = { up: 'pi-arrow-up', down: 'pi-arrow-down', same: 'pi-minus' } as const;
</script>

<template>
  <section class="round-over" :class="{ 'round-over--still': still }" :style="{ '--sheet-slide': slideMs + 'ms' }"
           data-test="round-over">
    <h2 class="round-over-title" data-test="round-over-title">{{ format(ui.phase.roundOver, { n: round }) }}</h2>
    <ol class="places">
      <li v-for="(r, i) in rows" :key="r.seat" class="place" data-test="round-over-place"
          :style="{ '--place-delay': placeDelay(i) + 'ms' }">
        <span class="place-label" data-test="place-label">{{ format(ui.roundOver.place, { n: r.place ?? '', player: nameOf(r.seat) }) }}</span>
        <span class="place-tags">
          <span class="place-title" data-test="place-title">{{ titleText(r.title) }}</span>
          <span v-if="r.forbidden" class="place-forbidden" data-test="place-forbidden"
                :title="ui.summary.forbiddenLabel">{{ ui.summary.forbidden }}</span>
        </span>
        <span v-if="r.change" class="place-change" :class="`place-change--${r.change.direction}`"
              data-test="place-change" :data-change="r.change.direction">
          <i class="pi" :class="ICONS[r.change.direction]" aria-hidden="true"></i>
          <span class="sr-only">{{ r.change.text }}</span>
        </span>
        <span v-if="r.points !== null" class="place-points" data-test="place-points" :title="ui.summary.pointsLabel">{{ format(ui.summary.points, { n: r.points }) }}</span>
        <span v-if="r.total !== null" class="place-total" data-test="place-total" :title="ui.summary.totalLabel">{{ r.total }}</span>
        <span v-if="r.ready" class="place-ready" data-test="place-ready">
          <i class="pi pi-check" aria-hidden="true"></i><span class="sr-only">{{ ui.summary.ready }}</span>
        </span>
      </li>
    </ol>
    <Button v-if="final" data-test="final-scores" class="ready" :label="ui.roundOver.finalScores" severity="success" rounded
            @click="emit('continue')" />
    <Button v-else-if="canReady" data-test="ready" class="ready" :label="ui.roundOver.ready" severity="success" rounded
            @click="emit('ready')" />
    <p v-else class="waiting" data-test="round-over-waiting" role="status">{{ ui.roundOver.waiting }}</p>
  </section>
</template>

<style scoped>
.round-over {
  display: flex; flex-direction: column; align-items: center; gap: var(--space-2);
  box-sizing: border-box; width: 100%; max-width: 420px;
  padding: var(--space-2); background: rgba(0, 0, 0, 0.45); border-radius: 14px;
  animation: sheet-rise var(--sheet-slide, 0ms) ease-out both;
}
.place { animation: place-in 260ms ease-out both; animation-delay: var(--place-delay, 0ms); }
@keyframes sheet-rise { from { transform: translateY(100%); opacity: 0; } to { transform: none; opacity: 1; } }
@keyframes place-in { from { transform: translateY(8px) scale(0.96); opacity: 0; } to { transform: none; opacity: 1; } }
.round-over--still, .round-over--still .place { animation: none; }
@media (prefers-reduced-motion: reduce) { .round-over, .place { animation: none; } }
.round-over-title { margin: 0; font-family: var(--font-title); font-size: 1rem; color: var(--gold); }
.places { margin: 0; padding: 0; list-style: none; width: 100%; font-size: 0.85rem; color: rgba(255, 255, 255, 0.9); }
.place {
  display: grid; grid-template-columns: minmax(0, 1fr) auto 1.2rem 2.2rem 2.2rem 1rem; align-items: center;
  column-gap: var(--space-1); padding: 3px 0;
}
.place-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.place-tags { display: inline-flex; align-items: center; gap: 4px; }
.place-title { color: var(--gold); font-size: 0.75rem; }
.place-forbidden {
  font-size: 0.62rem; font-weight: 700; text-transform: uppercase; white-space: nowrap;
  color: rgb(var(--accent-negative-rgb)); border: 1px solid currentColor; border-radius: 999px; padding: 0 5px;
}
.place-change--up { color: #7bd88f; }
.place-change--down { color: #ff8f8f; }
.place-change--same { color: rgba(255, 255, 255, 0.5); }
.place-points { text-align: right; font-weight: 700; }
.place-total { text-align: right; color: rgba(255, 255, 255, 0.7); }
.place-ready { color: #7bd88f; }
.sr-only {
  position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0;
  overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0;
}
.ready.p-button { min-height: 44px; min-width: 140px; }
.waiting { margin: 0; font-size: 0.85rem; color: rgba(255, 255, 255, 0.8); }
</style>
