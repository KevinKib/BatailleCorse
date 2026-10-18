<script setup lang="ts">
import { computed } from 'vue';
import TotalsTable from '../table/TotalsTable.vue';
import type { StandingRow } from '../../model/kobo/roundScores';
import { useI18n } from '../../composables/useI18n';

// The final totals in the slot of the end screen: rank, name and total of every player still at the table, lowest
// total first. The shared table of totals (`components/table/TotalsTable.vue`) with Kobo's texts.
const props = defineProps<{
  rows: StandingRow[];
  nameOf: (seat: number) => string;
  mySeat: number;
}>();
const ui = useI18n().koboUi;
const totals = ui.end.totals;
const rows = computed(() => props.rows.map(row => ({
  seat: row.seat, rank: row.rank, total: row.total, pill: row.over ? { text: totals.over, test: 'pill-over' } : null,
})));
</script>

<template>
  <TotalsTable :rows="rows" :name-of="nameOf" :my-seat="mySeat" :you="ui.you"
               :labels="{ title: totals.title, rank: totals.rank, player: totals.player, total: totals.total }" />
</template>
