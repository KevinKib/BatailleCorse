<script setup lang="ts">
import { computed } from 'vue';
import type { RoundRow } from '../../model/kobo/roundScores';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';

// The scores of the round that just ended, one row per player: hand value, round points, total before → after,
// and the markers of the round (who called Kobo, who received the 10 points, who is ready). Everything
// comes from the server's `lastRound`; names are user input, only ever rendered as text.
const props = defineProps<{
  rows: RoundRow[];
  nameOf: (seat: number) => string;
  mySeat: number;
  /** The seats ready for the next round; null when nobody is being waited for (no ticks). */
  readySeats: number[] | null;
}>();
const ui = useI18n().koboUi;
const sheet = ui.sheet;

const lines = computed(() => props.rows.map(r => ({
  ...r,
  name: r.seat === props.mySeat ? ui.you : props.nameOf(r.seat),
  ready: props.readySeats?.includes(r.seat) ?? false,
})));
</script>

<template>
  <table class="scores" data-test="scores" :aria-label="sheet.tableCaption">
    <thead>
      <tr>
        <th scope="col" class="col-player">{{ sheet.columns.player }}</th>
        <th scope="col" class="col-num">{{ sheet.columns.hand }}</th>
        <th scope="col" class="col-num">{{ sheet.columns.round }}</th>
        <th scope="col" class="col-total">{{ sheet.columns.total }}</th>
      </tr>
    </thead>
    <tbody>
      <tr v-for="line in lines" :key="line.seat" class="score-row" :class="{ 'score-row--me': line.seat === mySeat }"
          :data-test="`score-row-${line.seat}`">
        <th scope="row" class="col-player">
          <span class="name" data-test="score-name" :title="line.name">{{ line.name }}</span>
          <span v-if="line.announcer || line.gift || line.ready" class="pills">
            <span v-if="line.announcer" class="pill pill--kobo" data-test="pill-kobo">{{ sheet.pills.kobo }}</span>
            <span v-if="line.gift" class="pill pill--gift" data-test="pill-gift"
                  :aria-label="line.seat === mySeat ? sheet.pills.giftLabelYou : format(sheet.pills.giftLabel, { player: line.name })">{{ sheet.pills.gift }}</span>
            <span v-if="line.ready" class="pill pill--ready" :data-test="`ready-${line.seat}`">
              <i class="pi pi-check" aria-hidden="true"></i>{{ sheet.ready }}
            </span>
          </span>
        </th>
        <td class="col-num" data-test="score-hand">{{ line.handValue }}</td>
        <td class="col-num" data-test="score-round">{{ line.roundPoints }}</td>
        <td class="col-total">
          <span data-test="score-total">{{ format(sheet.totalChange, { before: line.totalBefore, after: line.totalAfter }) }}</span>
        </td>
      </tr>
    </tbody>
  </table>
</template>

<style scoped>
.scores {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
  color: rgba(255, 255, 255, 0.9);
}
th, td { padding: 4px 6px; text-align: right; font-weight: 700; vertical-align: middle; }
thead th {
  font-size: 0.875rem;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.55);
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}
.col-player { text-align: left; width: 46%; max-width: 0; }
.col-num { width: 14%; font-variant-numeric: tabular-nums; }
.col-total { display: table-cell; font-variant-numeric: tabular-nums; white-space: nowrap; }
.score-row { border-bottom: 1px solid rgba(255, 255, 255, 0.06); }
.score-row--me { background: rgba(var(--accent-active-rgb), 0.12); }
.name { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pills { display: flex; flex-wrap: wrap; gap: 3px; margin-top: 2px; }
.col-total .pill { display: block; margin-top: 2px; margin-left: auto; width: max-content; }
.pill {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 0 7px;
  font-size: 0.68rem;
  font-weight: 700;
  line-height: 1.5;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  white-space: nowrap;
}
.pill--kobo { color: var(--gold); border-color: var(--gold); }
.pill--gift { color: #fff; background: rgba(var(--accent-negative-rgb), 0.45); border-color: rgba(var(--accent-negative-rgb), 0.8); }
.pill--ready { color: #9be3b0; border-color: rgba(155, 227, 176, 0.6); }
</style>
