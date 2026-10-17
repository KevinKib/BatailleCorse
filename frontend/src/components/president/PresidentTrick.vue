<script setup lang="ts">
import { computed } from 'vue';
import PlayingCard from '../PlayingCard.vue';
import type { PresidentTrick } from '../../model/president/PresidentState';
import type { PresidentChip } from '../../model/president/chips';
import { cardLabel, rankLabel } from '../../model/president/cardLabel';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';

// The pile: the top play of the trick, face up, with who made it, and the rule chips next to it (owner decision 6C,
// provisional). Only the top play is drawn: it is the one to beat.
const props = defineProps<{
  trick: PresidentTrick;
  mySeat: number;
  nameOf: (seat: number) => string;
  chips: PresidentChip[];
  /** Short lines under the pile: who took the trick and why, a forbidden finish. Announced to screen readers. */
  notes?: string[];
}>();
const ui = useI18n().presidentUi;

const top = computed(() => props.trick.plays.length ? props.trick.plays[props.trick.plays.length - 1] : null);
const by = computed(() => {
  if (!top.value) return '';
  return top.value.seat === props.mySeat
    ? ui.table.playedByYou
    : format(ui.table.playedBy, { player: props.nameOf(top.value.seat) });
});
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
    <div class="trick-plate">
      <template v-if="top">
        <div class="trick-cards" data-test="trick-cards">
          <span v-for="card in top.cards" :key="card.name" class="trick-card" data-test="trick-card" role="img"
                :aria-label="cardLabel(card, ui, false)">
            <PlayingCard :rank="card.rank" :suit="card.suit" />
          </span>
        </div>
        <p class="trick-by" data-test="trick-by">{{ by }}</p>
      </template>
      <p v-else class="trick-empty" data-test="trick-empty">{{ ui.table.emptyTrick }}</p>
    </div>
    <ul class="trick-notes" data-test="trick-notes" aria-live="polite">
      <li v-for="note in notes ?? []" :key="note" class="trick-note" data-test="trick-note">{{ note }}</li>
    </ul>
    <ul v-if="chipTexts.length" class="chips" data-test="rule-chips">
      <li v-for="chip in chipTexts" :key="chip.key" class="chip" data-test="rule-chip">{{ chip.text }}</li>
    </ul>
  </div>
</template>

<style scoped>
.trick { display: flex; flex-direction: column; align-items: center; gap: var(--space-1); min-width: 0; }
.trick-plate { display: flex; flex-direction: column; align-items: center; gap: 2px; min-height: 40px; }
.trick-notes { margin: 0; padding: 0; list-style: none; text-align: center; }
.trick-notes:empty { display: none; }
.trick-note { font-size: 0.75rem; line-height: 1.1rem; color: var(--gold); }
.trick-cards { display: flex; justify-content: center; }
.trick-card { display: block; width: var(--president-pile-card-w, 52px); }
.trick-card + .trick-card { margin-left: calc(var(--president-pile-card-w, 52px) * -0.45); }
.trick-card :deep(.playing_card) { display: block; width: 100%; height: auto; aspect-ratio: 167.575 / 243.1375; }
.trick-by, .trick-empty { margin: 0; font-size: 0.75rem; color: rgba(255, 255, 255, 0.85); text-align: center; }
.trick-empty { align-self: center; padding: 10px 0; }
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
