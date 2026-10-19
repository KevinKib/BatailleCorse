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
// play (`pileView`). The notes under the pile (who took the trick and why, a forbidden
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
  /** The cards of a closed trick have been swept toward the winner: the pile keeps its place but shows no card. */
  swept?: boolean;
  /** No badge animation (reduced motion, Instant). */
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
    <!-- The plate has three places that never change size: the cards (or "nothing played yet"), one line under them (who
         played, or the badge of a closed trick), and, beside or below, the row of chips. -->
    <div :key="plateKey" class="trick-plate" :class="{ 'trick-plate--closed': closure, 'trick-plate--still': still }">
      <div class="trick-stage" data-test="trick-stage">
        <div v-if="top" class="trick-cards" :class="{ 'trick-cards--swept': swept && closure }" data-test="trick-cards" :aria-hidden="swept && closure ? 'true' : undefined">
          <span v-for="card in top.cards" :key="card.name" class="trick-card"
                :data-test="closure ? 'closed-trick-card' : 'trick-card'" role="img"
                :aria-label="cardLabel(card, ui, false)">
            <PlayingCard :rank="card.rank" :suit="card.suit" />
          </span>
        </div>
        <p v-else-if="!closure" class="trick-empty" data-test="trick-empty">{{ ui.table.emptyTrick }}</p>
      </div>
      <div class="trick-line" data-test="trick-line">
        <!-- A closed trick: one line in place of who played (who leads says it), clear of the rings on the cards. -->
        <p v-if="closure" class="trick-closed trick-closed--line" data-test="trick-closed" :data-reason="closure.reason">
          <span class="trick-closed-reason" data-test="trick-closed-reason">{{ closedReason }}</span>
          <span class="trick-closed-leader" data-test="trick-closed-leader">{{ closedLeader }}</span>
        </p>
        <p v-else-if="top" class="trick-by" data-test="trick-by">{{ by }}</p>
      </div>
    </div>
    <ul class="trick-notes sr-only" data-test="trick-notes" aria-live="polite">
      <li v-for="note in notes ?? []" :key="note" class="trick-note" data-test="trick-note">{{ note }}</li>
    </ul>
    <!-- The rows of chips are always there: how many cards the trick holds first, then the rules in force. -->
    <ul class="chips" data-test="trick-chips">
      <li v-if="view.count > 0" class="chip trick-count" data-test="trick-count">
        <span class="trick-count-icon" aria-hidden="true"><i></i><i></i></span>{{ plural(ui.motion.trickCount, view.count) }}
      </li>
      <li v-for="chip in chipTexts" :key="chip.key" class="chip" data-test="rule-chip">{{ chip.text }}</li>
    </ul>
  </div>
</template>

<style scoped>
/* Fixed places: a text that comes or goes (who played, the badge, a chip) never moves the cards. */
.trick {
  --pile-card-w: var(--president-pile-card-w, 52px);
  --pile-line-h: 30px;
  display: flex; flex-direction: column; align-items: center; gap: var(--space-1); min-width: 0; width: 100%;
}
.trick-plate {
  position: relative; display: grid; grid-template-rows: auto var(--pile-line-h); row-gap: 2px; justify-items: center; align-items: start;
  width: 100%;
}
.trick-stage {
  display: flex; align-items: center; justify-content: center; width: 100%;
  height: calc(var(--pile-card-w) * 1.451);
}
.trick-cards { position: relative; display: flex; justify-content: center; }
/* Swept away: the place stays (the badge does not jump), the cards do not show. */
.trick-cards--swept { visibility: hidden; }
.trick-card { display: block; width: var(--pile-card-w); }
.trick-card + .trick-card { margin-left: calc(var(--pile-card-w) * -0.45); }
.trick-card :deep(.playing_card) { display: block; width: 100%; height: auto; aspect-ratio: 167.575 / 243.1375; }
.trick-line { display: flex; align-items: center; justify-content: center; width: 100%; height: var(--pile-line-h); min-width: 0; }
.trick-by, .trick-empty { margin: 0; font-size: 0.75rem; color: rgba(255, 255, 255, 0.85); text-align: center; }
.trick-empty { align-self: center; }

/* A closed trick: the closing play sits back (it is not the play to beat), the badge says why and who leads. */
.trick-plate--closed .trick-card { filter: saturate(0.5) brightness(0.7); }
.trick-closed {
  margin: 0;
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
/* One line: the reason, a dot, who leads. It fits the line under the cards. */
.trick-closed--line {
  flex-direction: row;
  flex-wrap: nowrap;
  justify-content: center;
  gap: 2px 8px;
  padding: 3px 12px;
  max-width: 100%;
  box-sizing: border-box;
}
.trick-closed--line .trick-closed-leader::before { content: "·"; margin-right: 8px; font-weight: 800; }
.trick-closed-reason { font-size: 0.8rem; font-weight: 800; letter-spacing: 0.04em; text-transform: uppercase; white-space: nowrap; }
.trick-closed-leader { font-size: 0.7rem; font-weight: 700; white-space: nowrap; }
/* No closing flash: the badge says it (owner decision, soft rings). */
.trick-plate--closed .trick-closed { animation: badge-in 260ms cubic-bezier(.2, .8, .2, 1.3) both; }
.trick-plate--still .trick-closed { animation: none; }
@keyframes badge-in {
  from { opacity: 0; transform: scale(0.7); }
  to { opacity: 1; transform: scale(1); }
}
@media (prefers-reduced-motion: reduce) {
  .trick-plate--closed .trick-closed { animation: none; }
}

/* The trick indicator: how many cards the trick holds. */
.trick-count {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  padding-left: 8px;
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
/* Wide: two rows of chips, top-aligned, whatever there is to say (up to six chips). */
.chips {
  width: 100%; height: calc(2 * 24px + 4px); box-sizing: border-box; list-style: none; margin: 0; padding: 0;
  display: flex; flex-wrap: wrap; justify-content: center; align-content: flex-start; gap: 4px;
}
.chip {
  box-sizing: border-box;
  height: 24px;
  display: inline-flex;
  align-items: center;
  font-size: 0.7rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 999px;
  padding: 0 10px;
  white-space: nowrap;
}

/* The table is wide but short, on a phone and on a screen: the cards on the left, the chips on their right (three fixed
   rows, a second column past that), the closed badge under the cards. The felt never scrolls inside itself, nor does the
   pile reach the seat at the top of the ring, whatever the rules in force. */
@media all {
  .trick { --pile-line-h: 30px; flex-direction: row; justify-content: center; align-items: center; gap: var(--space-2); }
  /* The plate keeps one size whatever it shows (cards and who played, or cards and the closed badge), so that the cards
     never move when a trick closes: the rings drawn on them a moment earlier stay on them. */
  .trick-plate { flex: 0 0 calc(var(--pile-card-w) * 2.8 + 10px); width: calc(var(--pile-card-w) * 2.8 + 10px); min-width: 0; }
  .trick-closed--line { flex-direction: column; flex-wrap: nowrap; gap: 0; padding: 2px 6px; }
  .trick-closed--line .trick-closed-leader::before { content: none; }
  .trick-closed-reason { font-size: 0.66rem; letter-spacing: 0; }
  .trick-closed-leader { font-size: 0.62rem; }
  .chips {
    flex: 1 1 0;
    min-width: 0;
    width: auto;
    height: calc(var(--pile-card-w) * 1.451 + 2px + var(--pile-line-h));
    display: grid;
    grid-auto-flow: column;
    grid-template-rows: repeat(3, minmax(0, 1fr));
    grid-auto-columns: minmax(0, 1fr);
    align-content: stretch;
    justify-items: start;
    gap: 3px 4px;
  }
  .chip {
    height: auto; max-height: 100%; align-self: center; max-width: 100%; white-space: normal; border-radius: 10px;
    padding: 1px 7px; line-height: 1.2; overflow: hidden;
  }
}
</style>
