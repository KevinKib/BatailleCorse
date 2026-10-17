<script setup lang="ts">
import type { StandingRow } from '../../model/kobo/roundScores';
import { useI18n } from '../../composables/useI18n';

// The final totals in the slot of the end screen: rank, name and total of every player still at the table, lowest
// total first. Names are user input, only ever rendered as text.
defineProps<{
  rows: StandingRow[];
  nameOf: (seat: number) => string;
  mySeat: number;
}>();
const ui = useI18n().koboUi;
const totals = ui.end.totals;
</script>

<template>
  <table class="totals" data-test="final-totals" :aria-label="totals.title">
    <thead>
      <tr>
        <th scope="col" class="col-rank">{{ totals.rank }}</th>
        <th scope="col" class="col-name">{{ totals.player }}</th>
        <th scope="col" class="col-total">{{ totals.total }}</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="row in rows" :key="row.seat" class="final-row" :class="{ 'final-row--me': row.seat === mySeat }"
          :data-test="`final-row-${row.seat}`">
        <td class="col-rank" data-test="final-rank">{{ row.rank }}</td>
        <th scope="row" class="col-name">
          <span class="name" data-test="final-name" :title="row.seat === mySeat ? ui.you : nameOf(row.seat)">{{
            row.seat === mySeat ? ui.you : nameOf(row.seat) }}</span>
        </th>
        <td class="col-total">
          <span data-test="final-total">{{ row.total }}</span>
          <span v-if="row.over" class="pill" data-test="pill-over">{{ totals.over }}</span>
        </td>
      </tr>
    </tbody>
  </table>
</template>

<style scoped>
.totals { width: min(17rem, calc(100vw - 8rem)); table-layout: fixed; border-collapse: collapse; font-size: 0.9rem; color: rgba(255, 255, 255, 0.92); }
th, td { padding: 4px 8px; font-weight: 600; text-align: left; vertical-align: middle; }
thead th {
  font-size: 0.68rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.55);
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}
.col-rank { width: 3rem; text-align: center; font-variant-numeric: tabular-nums; }
.col-name { overflow: hidden; }
.col-total { width: 6.5rem; text-align: right; white-space: nowrap; font-variant-numeric: tabular-nums; }
.final-row { border-bottom: 1px solid rgba(255, 255, 255, 0.06); }
.final-row--me { background: rgba(var(--accent-active-rgb), 0.12); }
.name { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pill {
  margin-left: 6px;
  padding: 0 7px;
  font-size: 0.68rem;
  font-weight: 700;
  line-height: 1.5;
  color: #fff;
  background: rgba(var(--accent-negative-rgb), 0.45);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.8);
  border-radius: 999px;
  white-space: nowrap;
}
</style>
