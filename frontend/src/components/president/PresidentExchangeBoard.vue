<script setup lang="ts">
import { useI18n } from '../../composables/useI18n';
import PlayingCard from '../PlayingCard.vue';
import { cardLabel } from '../../model/president/cardLabel';
import type { ExchangeBoard, ExchangeBoardRow } from '../../model/president/exchangeBoard';
import { format, plural } from '../../locales/format';
import type Card from '../../model/Card';

// The board "The exchange": one row per pair of the round, in the pile's slot, so that everyone at the table sees who
// gives what to whom (a Neutral too). The faces show in my own pair only (the server sends no others); the other pairs
// show how many cards go each way. A row is done only once its cards have landed (the screen says which legs still
// fly). It fills the box it is given, never resizes it, and scrolls its rows inside itself if they do not all fit.
const props = defineProps<{
  board: ExchangeBoard;
  nameOf: (seat: number) => string;
  /** The title text of a seat this round; null when it has none (round 1). */
  titleOf: (seat: number) => string | null;
  mySeat: number;
  /** I may say I have seen the exchange (the Seen button works). */
  canSee: boolean;
  /** The 3 s reading time that follows the last landing, drawn as a bar; null when there is nothing to read. */
  reading: { startsInMs: number; durationMs: number; key: number } | null;
  still: boolean;
}>();
const emit = defineEmits<{ seen: [] }>();

const ui = useI18n().presidentUi;
const text = ui.exchange.board;

const minis = (row: ExchangeBoardRow, faces: Card[] | null): { key: string; card: Card | null }[] =>
  Array.from({ length: row.count }, (_, i) => ({ key: `${i}`, card: faces?.[i] ?? null }));
const choosing = (row: ExchangeBoardRow) => row.high === props.mySeat
  ? format(text.youChoose, { n: row.count })
  : format(text.choosing, { player: props.nameOf(row.high), n: row.count });
const sentence = (row: ExchangeBoardRow) => plural(text.row, row.count, {
  high: props.nameOf(row.high), low: props.nameOf(row.low), state: text.states[row.state],
});
const waitingForReturn = (row: ExchangeBoardRow) => row.state === 'giving' || row.state === 'choosing';
const barShown = () => props.reading !== null && !props.still && props.reading.durationMs > 0;
</script>

<template>
  <section class="exchange-board" :class="{ 'exchange-board--still': still }" data-test="exchange-board" role="region"
           :aria-label="text.title">
    <header class="head">
      <h3 class="title" data-test="board-title">{{ text.title }}</h3>
      <p v-if="board.neutral" class="neutral" data-test="board-neutral">{{ text.neutral }}</p>
      <button type="button" class="seen" :class="{ 'seen--off': !canSee }" data-test="board-seen"
              :aria-label="text.seen" :title="text.seen" :disabled="!canSee" :aria-hidden="canSee ? undefined : 'true'"
              :tabindex="canSee ? undefined : -1" @click="emit('seen')">
        <i class="pi pi-check" aria-hidden="true"></i>
      </button>
    </header>
    <ul class="rows" data-test="board-rows">
      <li v-for="row in board.rows" :key="row.key" class="row" :class="{ 'row--mine': row.mine }" data-test="board-row"
          :data-mine="row.mine" :data-state="row.state">
        <span class="sentence" data-test="board-sentence">{{ sentence(row) }}</span>
        <span class="seat seat--low" data-test="board-low" aria-hidden="true">
          <span class="name">{{ nameOf(row.low) }}</span>
          <span v-if="titleOf(row.low)" class="rank">{{ titleOf(row.low) }}</span>
        </span>
        <span class="leg" data-test="board-best" aria-hidden="true">
          <span class="cards">
            <PlayingCard v-for="m in minis(row, row.best)" :key="m.key" :hidden="!m.card" :rank="m.card?.rank ?? 'ace'"
                         :suit="m.card?.suit ?? 'spade'" :alt="m.card ? cardLabel(m.card, ui, false) : ''" />
          </span>
          <i class="pi pi-arrow-right arrow" aria-hidden="true"></i>
        </span>
        <span class="leg leg--back" :class="{ 'leg--dim': row.state === 'giving' }" data-test="board-back" aria-hidden="true">
          <template v-if="waitingForReturn(row)"><span class="choosing">{{ choosing(row) }}</span></template>
          <template v-else>
            <i class="pi pi-arrow-left arrow" aria-hidden="true"></i>
            <span class="cards">
              <PlayingCard v-for="m in minis(row, row.back)" :key="m.key" :hidden="!m.card" :rank="m.card?.rank ?? 'ace'"
                           :suit="m.card?.suit ?? 'spade'" :alt="m.card ? cardLabel(m.card, ui, false) : ''" />
            </span>
          </template>
        </span>
        <span class="seat seat--high" data-test="board-high" aria-hidden="true">
          <span class="name">{{ nameOf(row.high) }}</span>
          <span v-if="titleOf(row.high)" class="rank">{{ titleOf(row.high) }}</span>
        </span>
        <span class="mark" :class="`mark--${row.state}`" data-test="board-mark" :data-state="row.state"
              :aria-label="text.states[row.state]" :title="text.states[row.state]">
          <i v-if="row.state === 'done'" class="pi pi-check" aria-hidden="true"></i>
        </span>
      </li>
    </ul>
    <div v-if="barShown()" class="bar" data-test="board-bar" aria-hidden="true">
      <span :key="reading!.key" class="fill" data-test="board-bar-fill"
            :style="{ animationDuration: `${reading!.durationMs}ms`, animationDelay: `${reading!.startsInMs}ms` }"></span>
    </div>
  </section>
</template>

<style scoped>
/* The board fills the box of the pile (absolute, over the trick plate that stays laid out under it): a size container,
   so that what it holds adapts to the room it is given and never to the screen. */
.exchange-board {
  position: relative; display: flex; flex-direction: column; box-sizing: border-box; width: 100%; height: 100%;
  padding: 4px 6px 6px; overflow: hidden; container-type: size; container-name: exchange-board;
  background: rgba(0, 0, 0, 0.55); border: 1px solid rgba(255, 255, 255, 0.12); border-radius: 12px;
  color: rgba(255, 255, 255, 0.85);
}
.head { flex: none; display: flex; align-items: center; gap: 8px; height: 24px; min-width: 0; }
.title {
  flex: none; margin: 0; font-size: 0.65rem; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase;
  color: var(--gold);
}
.neutral {
  flex: 1 1 auto; min-width: 0; margin: 0; font-size: 0.6rem; line-height: 1.1; overflow: hidden; text-overflow: ellipsis;
  white-space: nowrap; color: rgba(255, 255, 255, 0.8);
}
.seen {
  position: relative; flex: none; display: inline-flex; align-items: center; justify-content: center; width: 24px;
  height: 24px; margin-left: auto; padding: 0; border: 1px solid rgba(255, 255, 255, 0.35); border-radius: 50%;
  background: rgba(255, 255, 255, 0.08); color: #fff; font-size: 0.6rem; cursor: pointer;
}
/* A 44 px target around the 24 px button. */
.seen::before { content: ''; position: absolute; inset: -10px; }
.seen--off { visibility: hidden; }
.rows {
  flex: 1 1 auto; min-height: 0; margin: 0; padding: 0; list-style: none; display: flex; flex-direction: column; gap: 2px;
  overflow-y: auto; overscroll-behavior: contain;
}
.row {
  position: relative; flex: none; display: grid; grid-template-columns: minmax(0, 1fr) auto auto minmax(0, 1fr) 16px; align-items: center;
  column-gap: 4px; min-height: 22px; font-size: 0.65rem; color: rgba(255, 255, 255, 0.75);
}
.row--mine { color: #fff; font-weight: 700; }
.sentence {
  position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0; overflow: hidden; clip: rect(0 0 0 0);
  white-space: nowrap; border: 0;
}
.seat { display: flex; flex-direction: column; min-width: 0; line-height: 1.05; }
.seat--low { align-items: flex-end; text-align: right; }
.name, .rank { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }
.rank { font-size: 0.55rem; font-weight: 400; color: rgba(255, 255, 255, 0.55); }
.leg { display: inline-flex; align-items: center; gap: 2px; min-width: 0; }
.leg--dim { opacity: 0.55; }
.choosing { font-size: 0.6rem; font-weight: 400; font-style: italic; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.arrow { font-size: 0.5rem; color: var(--gold); }
.cards { display: inline-flex; }
.cards :deep(.playing_card) { display: block; width: 12px; height: 17px; }
.cards :deep(.playing_card:nth-child(n + 3)) { margin-left: -6px; }
.mark {
  box-sizing: border-box; display: inline-flex; align-items: center; justify-content: center; width: 14px; height: 14px;
  border-radius: 50%; border: 1px dashed rgba(255, 255, 255, 0.55); font-size: 0.5rem;
}
.mark--done { border: 1px solid rgb(var(--accent-positive-rgb)); background: rgba(var(--accent-positive-rgb), 0.2); color: rgb(var(--accent-positive-rgb)); }
.mark--returning { border-style: solid; }
.bar { position: absolute; left: 6px; right: 6px; bottom: 3px; height: 3px; border-radius: 2px; background: rgba(255, 255, 255, 0.12); overflow: hidden; }
.fill {
  display: block; width: 100%; height: 100%; background: var(--gold); transform-origin: 0 50%; transform: scaleX(0);
  animation-name: exchange-board-read; animation-timing-function: linear; animation-fill-mode: both;
}
@keyframes exchange-board-read { from { transform: scaleX(0); } to { transform: scaleX(1); } }
.rows { padding-bottom: 5px; }

/* Wide: bigger rows and cards. */
@media (min-width: 768px) {
  .title { font-size: 0.7rem; }
  .row { min-height: 26px; font-size: 0.75rem; grid-template-columns: minmax(0, 1fr) auto auto minmax(0, 1fr) 18px; column-gap: 8px; }
  .rank { font-size: 0.6rem; }
  .choosing { font-size: 0.7rem; }
  .neutral { font-size: 0.7rem; }
  .cards :deep(.playing_card) { width: 14px; height: 20px; }
  .cards :deep(.playing_card:nth-child(n + 3)) { margin-left: -7px; }
  .mark { width: 16px; height: 16px; }
}
/* Too little height for the title: it stays for assistive technology only. */
@container exchange-board (max-height: 79px) {
  .title {
    position: absolute; width: 1px; height: 1px; margin: -1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap;
  }
}
/* A narrow board: the titles go (the row's sentence says them), the names take the room. */
@container exchange-board (max-width: 300px) {
  .rank { display: none; }
}
.exchange-board--still .fill, .exchange-board--still .mark { transition: none; animation: none; }
</style>
