<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from '../../composables/useI18n';
import { format, ordinal } from '../../locales/format';
import { standingsOf } from '../../model/blackjack/standings';
import type { BlackjackSeat } from '../../model/blackjack/BlackjackState';

// The end screen body: the final places with their stacks (equal stacks share a place); in solo, the final stack
// and the net instead. Names are user input: only ever rendered as text.
const props = defineProps<{
  players: BlackjackSeat[];
  nameOf: (seat: number) => string;
  mySeat: number;
  solo: boolean;
  /** Solo only: final stack minus the starting stack. */
  net?: number;
}>();
const ui = useI18n().blackjackUi;

const rows = computed(() => standingsOf(props.players).map(r => ({
  seat: r.player.seat,
  place: ordinal(r.place, ui.ordinal),
  name: r.player.seat === props.mySeat ? ui.you : props.nameOf(r.player.seat),
  stack: format(ui.end.stackOf, { n: r.player.stack }),
  note: r.status === 'eliminated' ? ui.end.eliminated : r.status === 'forfeited' ? ui.end.forfeited : '',
  first: r.place === 1,
})));
const me = computed(() => props.players.find(p => p.seat === props.mySeat));
const soloLine = computed(() => {
  const net = props.net ?? 0;
  return format(ui.end.soloFinal, { stack: me.value?.stack ?? 0, net: net > 0 ? `+${net}` : String(net) });
});
</script>

<template>
  <p v-if="solo" class="bj-solo" data-test="solo-final">{{ soloLine }}</p>
  <ol v-else class="bj-places" :aria-label="ui.end.places" data-test="places">
    <li v-for="row in rows" :key="row.seat" class="bj-row"
        :class="{ 'bj-row--me': row.seat === mySeat, 'bj-row--first': row.first }" data-test="place-row">
      <strong data-test="place">{{ row.place }}</strong>
      <span class="bj-name" data-test="place-name" :title="row.name">{{ row.name }}</span>
      <span data-test="place-stack">{{ row.stack }}</span>
      <span v-if="row.note" class="bj-note" data-test="place-note">{{ row.note }}</span>
    </li>
  </ol>
</template>

<style scoped>
.bj-solo { margin: 0; font-weight: 700; }
.bj-places { width: 100%; margin: 0; padding: 0; list-style: none; display: flex; flex-direction: column; gap: var(--space-1); }
.bj-row { display: grid; grid-template-columns: 44px minmax(0, 1fr) auto auto; align-items: center; gap: var(--space-2); min-height: 34px;
  padding: 0 var(--space-3); border-radius: 8px; background: rgba(255, 255, 255, 0.05); font-size: 0.875rem; }
.bj-row--first { color: var(--gold); }
.bj-row--me { background: rgba(245, 200, 66, 0.18); }
.bj-name { min-width: 0; text-align: left; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bj-note { font-size: 0.6875rem; opacity: 0.75; }
</style>
