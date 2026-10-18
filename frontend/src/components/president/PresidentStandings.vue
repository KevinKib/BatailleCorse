<script setup lang="ts">
import { computed } from 'vue';
import TotalsTable from '../table/TotalsTable.vue';
import type { PresidentStanding } from '../../model/president/PresidentState';
import { useI18n } from '../../composables/useI18n';

// The seats ranked by points, as the server sends them (`standings`): the running score of any match, and the
// final one. Seats with the same points share a place. The shared table of totals (Kobo's final totals look), with
// President's texts. Names are user input: only ever rendered as text.
const props = defineProps<{
  standings: PresidentStanding[];
  nameOf: (seat: number) => string;
  mySeat: number;
}>();
const ui = useI18n().presidentUi;
const rows = computed(() => props.standings.map((s, _i, all) => ({
  seat: s.seat,
  rank: all.findIndex(o => o.points === s.points) + 1,
  total: s.points,
})));
const labels = computed(() => ({
  title: ui.standings.title, rank: ui.standings.rank, player: ui.standings.player, total: ui.standings.points,
}));
</script>

<template>
  <TotalsTable :rows="rows" :name-of="nameOf" :my-seat="mySeat" :you="ui.you" :labels="labels" />
</template>
