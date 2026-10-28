<script setup lang="ts">
import { computed } from 'vue';
import TotalsTable, { type TotalsRow } from '../table/TotalsTable.vue';
import { useI18n } from '../../composables/useI18n';
import type { KiloDeMerdePlace, KiloDeMerdePlayer } from '../../model/kilodemerde/KiloDeMerdeState';

// The standings over the shared table of totals: the final places the server ranked (shared places kept) once the game is
// over, else the seats ranked by the kilos they hold (fewest first, equal totals sharing the rank). Names are user input.
const props = defineProps<{
  players: KiloDeMerdePlayer[];
  /** The server's final places; null while the game goes on. */
  places: KiloDeMerdePlace[] | null;
  nameOf: (seat: number) => string;
  mySeat: number;
}>();
const ui = useI18n().kiloDeMerdeUi;

const rows = computed<TotalsRow[]>(() => {
  if (props.places) {
    return [...props.places]
      .sort((a, b) => a.place - b.place || a.seat - b.seat)
      .map(p => ({ seat: p.seat, rank: p.place, total: p.kilos }));
  }
  const sorted = props.players.filter(p => p.active).sort((a, b) => a.kilos - b.kilos || a.seat - b.seat);
  return sorted.map((p, i) => ({
    seat: p.seat,
    // Competition ranking: a seat tied with the one before keeps its rank.
    rank: i > 0 && sorted[i - 1].kilos === p.kilos ? 0 : i + 1,
    total: p.kilos,
  })).reduce<TotalsRow[]>((acc, row) => {
    acc.push(row.rank === 0 ? { ...row, rank: acc[acc.length - 1].rank } : row);
    return acc;
  }, []);
});
const labels = computed(() => ({ title: ui.table.standings, rank: '#', player: ui.standings.player, total: ui.standings.kilos }));
</script>

<template>
  <TotalsTable :rows="rows" :name-of="nameOf" :my-seat="mySeat" :you="ui.you" :labels="labels" />
</template>
