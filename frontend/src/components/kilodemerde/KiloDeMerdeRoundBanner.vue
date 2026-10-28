<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import { roundLineText } from '../../model/kilodemerde/roundLineText';
import type { KiloDeMerdeRace } from '../../model/kilodemerde/KiloDeMerdeState';
import type { RoundLine } from '../../model/kilodemerde/roundSummary';

// What the table says about a race and a round: while a race is open, who slapped first and the live order; once the
// round is over, one line per draw ("X was last and takes K♠: 13 kg", a false slap, a flush, nobody, a voided round).
// Names are user input: only ever rendered as text.
const props = defineProps<{
  race: KiloDeMerdeRace | null;
  lines: RoundLine[];
  nameOf: (seat: number) => string;
}>();
const ui = useI18n().kiloDeMerdeUi;

const texts = computed(() => props.lines.map(line => roundLineText(line, props.nameOf, ui.outcome)));
const visible = computed(() => props.race !== null || props.lines.length > 0);
</script>

<template>
  <div v-if="visible" class="banner" data-test="round-banner">
    <div v-if="race" class="race" data-test="race-banner">
      <strong class="race-title">{{ format(ui.race.slapped, { name: nameOf(race.firstSlapper) }) }}</strong>
      <ol class="race-order" data-test="race-order" :aria-label="ui.race.order">
        <li v-for="(seat, i) in race.order" :key="seat" class="race-item">
          <span class="race-rank">{{ i + 1 }}</span><span class="race-name">{{ nameOf(seat) }}</span>
        </li>
      </ol>
    </div>
    <template v-else>
      <p v-for="(text, i) in texts" :key="i" class="line" data-test="round-line">{{ text }}</p>
    </template>
  </div>
</template>

<style scoped>
.banner {
  box-sizing: border-box;
  max-width: min(32rem, calc(100% - 16px));
  padding: var(--space-2) var(--space-3);
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.82);
  border: 1px solid rgba(245, 200, 66, 0.7);
  color: #fff;
  text-align: center;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.5);
}
.race { display: flex; flex-direction: column; align-items: center; gap: var(--space-1); }
.race-title { font-size: 0.9375rem; color: var(--gold); overflow-wrap: anywhere; }
.race-order { margin: 0; padding: 0; list-style: none; display: flex; flex-wrap: wrap; justify-content: center; gap: var(--space-1) var(--space-2); }
.race-item { display: inline-flex; align-items: center; gap: var(--space-1); font-size: 0.75rem; }
.race-rank {
  min-width: 18px; height: 18px; border-radius: 9px; background: var(--gold); color: #141413;
  font-weight: 700; line-height: 18px; text-align: center; font-variant-numeric: tabular-nums;
}
.race-name { max-width: 7rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.line { margin: 0; font-size: 0.875rem; font-weight: 700; overflow-wrap: anywhere; }
.line + .line { margin-top: var(--space-1); }
</style>
