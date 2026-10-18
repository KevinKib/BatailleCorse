<script setup lang="ts">
// The table of totals shared by the card tables (Kobo's final totals, President's standings): rank, name and total of
// every player, my row tinted, an optional pill after a total. The rows come ranked; the texts are the game's.
// Names are user input, only ever rendered as text.
export interface TotalsRow {
  seat: number;
  rank: number;
  total: number;
  /** A short tag after the total ("Over 100"), with its own test id. */
  pill?: { text: string; test: string } | null;
}

defineProps<{
  rows: TotalsRow[];
  nameOf: (seat: number) => string;
  mySeat: number;
  /** My own name in the table ("You"). */
  you: string;
  labels: { title: string; rank: string; player: string; total: string };
}>();
</script>

<template>
  <table class="totals" data-test="final-totals" :aria-label="labels.title">
    <thead>
      <tr>
        <th scope="col" class="col-rank">{{ labels.rank }}</th>
        <th scope="col" class="col-name">{{ labels.player }}</th>
        <th scope="col" class="col-total">{{ labels.total }}</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="row in rows" :key="row.seat" class="final-row" :class="{ 'final-row--me': row.seat === mySeat }"
          :data-test="`final-row-${row.seat}`">
        <td class="col-rank" data-test="final-rank">{{ row.rank }}</td>
        <th scope="row" class="col-name">
          <span class="name" data-test="final-name" :title="row.seat === mySeat ? you : nameOf(row.seat)">{{
            row.seat === mySeat ? you : nameOf(row.seat) }}</span>
        </th>
        <td class="col-total">
          <span data-test="final-total">{{ row.total }}</span>
          <span v-if="row.pill" class="pill" :data-test="row.pill.test">{{ row.pill.text }}</span>
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
