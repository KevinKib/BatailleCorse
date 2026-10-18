<script setup lang="ts">
import { computed } from 'vue';
import PlayingCard from '../PlayingCard.vue';
import type { PresidentTrick } from '../../model/president/PresidentState';
import type { PresidentChip } from '../../model/president/chips';
import type { PileView } from '../../model/president/trickView';
import { cardLabel, rankLabel } from '../../model/president/cardLabel';
import { useI18n } from '../../composables/useI18n';
import { format, plural } from '../../locales/format';

// The pile: the top play of the trick, face up, with who made it, a small indicator of how many cards the trick holds
// (spec §11 decision 7, in place of the covered plays) and the rule chips next to it (decision 6C). A trick the latest
// move closed keeps its closing play on the pile, dimmed, with a badge that says why and who leads, until the next
// play (`pileView`); it flashes once when it closes. The notes under the pile (who took the trick and why, a forbidden
// finish) are a secondary line for screen readers: the badge says it on screen.
const props = defineProps<{
  trick: PresidentTrick;
  mySeat: number;
  nameOf: (seat: number) => string;
  chips: PresidentChip[];
  /** Short lines read out by screen readers: who took the trick and why, a forbidden finish. */
  notes?: string[];
  /** What the pile shows (the store's `pile`); without it, the top play of `trick`. */
  pile?: PileView;
  /** No closing flash (reduced motion, Instant). */
  still?: boolean;
}>();
const ui = useI18n().presidentUi;

const view = computed<PileView>(() => {
  if (props.pile) return props.pile;
  const plays = props.trick.plays;
  return {
    play: plays.length ? plays[plays.length - 1] : null,
    closure: null,
    count: plays.reduce((n, p) => n + p.cards.length, 0),
  };
});
const top = computed(() => view.value.play);
const closure = computed(() => view.value.closure);
const by = computed(() => {
  if (!top.value) return '';
  return top.value.seat === props.mySeat
    ? ui.table.playedByYou
    : format(ui.table.playedBy, { player: props.nameOf(top.value.seat) });
});
const closedReason = computed(() => closure.value ? ui.motion.closed[closure.value.reason] ?? ui.motion.closedOther : '');
const closedLeader = computed(() => {
  const c = closure.value;
  if (!c) return '';
  return c.winner === props.mySeat ? ui.motion.youLead : format(ui.motion.leads, { player: props.nameOf(c.winner) });
});
// A new closure restarts the flash.
const plateKey = computed(() => closure.value ? `closed-${closure.value.winner}-${closure.value.reason}-${closure.value.size}-${top.value?.cards.map(c => c.name).join('+') ?? ''}` : 'open');
const chipTexts = computed(() => props.chips.flatMap((chip) => {
  const template = ui.chips[chip.key];
  if (template === undefined) return [];
  const vars = { ...chip.vars };
  if (typeof vars.rank === 'string') vars.rank = rankLabel(vars.rank, ui);
  return [{ key: chip.key, text: format(template, vars) }];
}));
</script>

<template>
  <div class="trick" data-test="trick">
    <div :key="plateKey" class="trick-plate" :class="{ 'trick-plate--closed': closure, 'trick-plate--still': still }">
      <template v-if="top">
        <div class="trick-cards" data-test="trick-cards">
          <span v-for="card in top.cards" :key="card.name" class="trick-card"
                :data-test="closure ? 'closed-trick-card' : 'trick-card'" role="img"
                :aria-label="cardLabel(card, ui, false)">
            <PlayingCard :rank="card.rank" :suit="card.suit" />
          </span>
        </div>
        <p class="trick-by" data-test="trick-by">{{ by }}</p>
      </template>
      <p v-else-if="!closure" class="trick-empty" data-test="trick-empty">{{ ui.table.emptyTrick }}</p>
      <p v-if="closure" class="trick-closed" data-test="trick-closed" :data-reason="closure.reason">
        <span class="trick-closed-reason" data-test="trick-closed-reason">{{ closedReason }}</span>
        <span class="trick-closed-leader" data-test="trick-closed-leader">{{ closedLeader }}</span>
      </p>
    </div>
    <p v-if="view.count > 0" class="trick-count" data-test="trick-count">
      <span class="trick-count-icon" aria-hidden="true"><i></i><i></i></span>{{ plural(ui.motion.trickCount, view.count) }}
    </p>
    <ul class="trick-notes sr-only" data-test="trick-notes" aria-live="polite">
      <li v-for="note in notes ?? []" :key="note" class="trick-note" data-test="trick-note">{{ note }}</li>
    </ul>
    <ul v-if="chipTexts.length" class="chips" data-test="rule-chips">
      <li v-for="chip in chipTexts" :key="chip.key" class="chip" data-test="rule-chip">{{ chip.text }}</li>
    </ul>
  </div>
</template>

<style scoped>
.trick { display: flex; flex-direction: column; align-items: center; gap: var(--space-1); min-width: 0; }
.trick-plate { position: relative; display: flex; flex-direction: column; align-items: center; gap: 2px; min-height: 40px; }
.trick-cards { display: flex; justify-content: center; }
.trick-card { display: block; width: var(--president-pile-card-w, 52px); }
.trick-card + .trick-card { margin-left: calc(var(--president-pile-card-w, 52px) * -0.45); }
.trick-card :deep(.playing_card) { display: block; width: 100%; height: auto; aspect-ratio: 167.575 / 243.1375; }
.trick-by, .trick-empty { margin: 0; font-size: 0.75rem; color: rgba(255, 255, 255, 0.85); text-align: center; }
.trick-empty { align-self: center; padding: 10px 0; }

/* A closed trick: the closing play sits back (it is not the play to beat), the badge says why and who leads. */
.trick-plate--closed .trick-cards { filter: saturate(0.5) brightness(0.7); }
.trick-plate--closed .trick-by { opacity: 0.7; }
.trick-closed {
  margin: 2px 0 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  padding: 4px 12px;
  text-align: center;
  color: #1b1b1b;
  background: var(--gold);
  border-radius: 10px;
  box-shadow: 0 3px 12px rgba(0, 0, 0, 0.5);
}
.trick-closed-reason { font-size: 0.8rem; font-weight: 800; letter-spacing: 0.04em; text-transform: uppercase; }
.trick-closed-leader { font-size: 0.7rem; font-weight: 700; }
/* The closing flash: a gold glow around the closed play, once. */
.trick-plate--closed::before {
  content: "";
  position: absolute;
  inset: -10px -14px;
  border-radius: 14px;
  pointer-events: none;
  box-shadow: 0 0 0 3px rgba(255, 210, 63, 0.9), 0 0 26px 8px rgba(255, 210, 63, 0.7);
  opacity: 0;
  animation: closing-flash 900ms ease-out both;
}
.trick-plate--closed .trick-closed { animation: badge-in 260ms cubic-bezier(.2, .8, .2, 1.3) both; }
.trick-plate--still::before, .trick-plate--still .trick-closed { animation: none; }
@keyframes closing-flash {
  0% { opacity: 0; transform: scale(1.15); }
  20% { opacity: 1; transform: scale(1); }
  100% { opacity: 0; }
}
@keyframes badge-in {
  from { opacity: 0; transform: scale(0.7); }
  to { opacity: 1; transform: scale(1); }
}
@media (prefers-reduced-motion: reduce) {
  .trick-plate--closed::before, .trick-plate--closed .trick-closed { animation: none; }
}

/* The trick indicator: how many cards the trick holds. */
.trick-count {
  margin: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.7rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 999px;
  padding: 2px 10px 2px 8px;
}
.trick-count-icon { position: relative; display: inline-block; width: 12px; height: 13px; }
.trick-count-icon i {
  position: absolute;
  width: 8px;
  height: 11px;
  border: 1.5px solid currentColor;
  border-radius: 2px;
  background: rgba(0, 0, 0, 0.4);
}
.trick-count-icon i:first-child { left: 0; top: 2px; transform: rotate(-10deg); }
.trick-count-icon i:last-child { left: 4px; top: 0; transform: rotate(8deg); }

.trick-notes { margin: 0; padding: 0; list-style: none; }
.sr-only {
  position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0;
  overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0;
}
.chips { max-width: 15rem; list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; justify-content: center; gap: 4px; }
.chip {
  font-size: 0.7rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 999px;
  padding: 2px 10px;
  white-space: nowrap;
}
</style>
