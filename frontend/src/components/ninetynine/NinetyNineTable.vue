<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import NinetyNineCenter from './NinetyNineCenter.vue';
import NinetyNineSeatPlate from './NinetyNineSeatPlate.vue';
import { useI18n } from '../../composables/useI18n';
import {
  centerBox, chooseLayout, directionArrows, plateSizeFor, radiiFor, ringCenters, trackRadii, type Frame,
} from '../../model/ninetynine/ringLayout';
import { seatsAtTable, seatsInTurnOrder } from '../../model/ninetynine/seatOrder';
import type Card from '../../model/Card';
import type { NinetyNineDirection, NinetyNinePlayer } from '../../model/ninetynine/NinetyNineState';

// The table of 99: every seat on a ring around the middle block (total, pile, stock), me at the bottom, seat order
// clockwise on screen, arrows on a dashed track for the direction of play. Where the felt is too small to keep 8 px
// between 12 plates (a narrow phone), the seats go to a rail above the middle block, a grid in turn order (the canvas's
// option B). The felt is measured; the model (`ringLayout.ts`) decides.
const props = defineProps<{
  players: NinetyNinePlayer[];
  mySeat: number;
  currentSeat: number;
  direction: NinetyNineDirection;
  total: number;
  pileTop: Card | null;
  stockSize: number;
  maxLives: number;
  nameOf: (seat: number) => string;
  /** The seat that just lost a life. */
  hitSeat?: number | null;
  disconnected?: (seat: number) => boolean;
  secondsRemaining?: (seat: number) => number | null;
  /** The size of the felt, given instead of measured (tests, previews). */
  frame?: Frame;
}>();
const ui = useI18n().ninetyNineUi;

const felt = ref<HTMLElement | null>(null);
const measured = ref<Frame | null>(null);
let observer: ResizeObserver | null = null;
onMounted(() => {
  const el = felt.value;
  if (!el) return;
  const read = () => {
    const box = el.getBoundingClientRect();
    if (box.width > 0 && box.height > 0) measured.value = { width: box.width, height: box.height };
  };
  read();
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(read);
    observer.observe(el);
  }
});
onBeforeUnmount(() => observer?.disconnect());

/** What the felt measures: the given size, else the measured one; unknown, a phone-sized felt stands in for the first paint. */
const known = computed<Frame | null>(() => props.frame ?? measured.value);
const FIRST_PAINT: Frame = { width: 344, height: 365 };
const frame = computed<Frame>(() => known.value ?? FIRST_PAINT);

const ring = computed(() => seatsAtTable(props.players, props.mySeat));
const layout = computed(() => chooseLayout(ring.value.length, known.value));
const plate = computed(() => plateSizeFor(frame.value.width));
const compact = computed(() => plate.value.w < 100);
const nameOfSeat = (seat: number) => seat === props.mySeat ? ui.you : props.nameOf(seat);

// Plates are placed by percentage of the felt, so that they follow it between two measures.
const plates = computed(() => {
  const f = frame.value;
  const { rx, ry } = radiiFor(f);
  return ringCenters(ring.value.length, f, rx, ry).map((c, i) => ({
    player: ring.value[i],
    style: {
      left: `calc(${(c.x / f.width) * 100}% - ${plate.value.w / 2}px)`,
      top: `calc(${(c.y / f.height) * 100}% - ${plate.value.h / 2}px)`,
      width: `${plate.value.w}px`,
      height: `${plate.value.h}px`,
    },
  }));
});

const arrows = computed(() => known.value ? directionArrows(known.value, props.direction === 'COUNTER_CLOCKWISE') : []);
const track = computed(() => trackRadii(frame.value));
const middleStyle = computed(() => {
  const box = centerBox(frame.value);
  return { left: '50%', top: `${(box.y + box.h / 2) / frame.value.height * 100}%` };
});

// The rail: the seats in the order play reaches them; a seat that is out has no number.
const rail = computed(() => {
  let n = 0;
  return seatsInTurnOrder(props.players, props.mySeat, props.direction).map(player => ({
    player,
    order: player.eliminated ? undefined : ++n,
  }));
});
const sizes = computed(() => (compact.value
  ? { '--nn-pile-w': '38px', '--nn-total-size': '44px', '--nn-card-w': '70px' }
  : { '--nn-pile-w': '64px', '--nn-total-size': '76px', '--nn-card-w': '90px' }));
</script>

<template>
  <div class="table" :class="`table--${layout}`" data-test="ninety-nine-table" :data-layout="layout"
       role="region" :aria-label="ui.table.opponents" :style="sizes">
    <div v-if="layout === 'rail'" class="rail" data-test="turn-order">
      <div class="rail-head">
        <span class="rail-title">{{ ui.table.turnOrder }}</span>
        <span v-if="direction === 'COUNTER_CLOCKWISE'" class="rail-reversed" data-test="rail-reversed">{{ ui.table.reversed }}</span>
      </div>
      <ol class="rail-list">
        <li v-for="seat in rail" :key="seat.player.seat" class="rail-item" :data-seat="seat.player.seat">
          <NinetyNineSeatPlate
            variant="chip"
            :player="seat.player"
            :label="nameOfSeat(seat.player.seat)"
            :max-lives="maxLives"
            :order="seat.order"
            :mine="seat.player.seat === mySeat"
            :current="seat.player.seat === currentSeat && !seat.player.eliminated"
            :hit="seat.player.seat === hitSeat"
            :disconnected="disconnected?.(seat.player.seat)"
            :seconds-remaining="secondsRemaining?.(seat.player.seat) ?? null" />
        </li>
      </ol>
    </div>

    <div ref="felt" class="felt">
      <div class="felt-oval" aria-hidden="true"></div>

      <template v-if="layout === 'ring'">
        <svg v-if="known" class="track" :width="known.width" :height="known.height" :viewBox="`0 0 ${known.width} ${known.height}`"
             role="img" :aria-label="direction === 'COUNTER_CLOCKWISE' ? ui.table.directionReversed : ui.table.directionClockwise">
          <ellipse :cx="known.width / 2" :cy="known.height / 2" :rx="track.rx" :ry="track.ry" class="track-line" />
          <polygon v-for="a in arrows" :key="a.angle" :points="a.points" class="track-arrow" />
        </svg>
        <div v-for="p in plates" :key="p.player.seat" class="ring-slot" :style="p.style" :data-seat="p.player.seat">
          <NinetyNineSeatPlate
            :player="p.player"
            :label="nameOfSeat(p.player.seat)"
            :max-lives="maxLives"
            :compact="compact"
            :mine="p.player.seat === mySeat"
            :current="p.player.seat === currentSeat && !p.player.eliminated"
            :hit="p.player.seat === hitSeat"
            :disconnected="disconnected?.(p.player.seat)"
            :seconds-remaining="secondsRemaining?.(p.player.seat) ?? null" />
        </div>
      </template>

      <div class="middle" :style="layout === 'ring' ? middleStyle : undefined">
        <NinetyNineCenter :total="total" :direction="direction" :pile-top="pileTop" :stock-size="stockSize" />
      </div>
      <div class="overlay"><slot name="overlay" /></div>
    </div>
  </div>
</template>

<style scoped>
.table { display: flex; flex-direction: column; gap: var(--space-2); flex: 1 1 0; min-height: 0; width: 100%; max-width: 1100px; margin: 0 auto; }
.felt {
  position: relative;
  flex: 1 1 0;
  min-height: 0;
  isolation: isolate;
}
/* The felt is an oval inside the box the plates stand on, as the canvas draws it. */
.felt-oval {
  position: absolute;
  inset: 9% 8%;
  border-radius: 50%;
  background: var(--table-felt-bg);
  border: var(--table-felt-border);
  box-shadow: var(--table-felt-shadow);
}
.table--rail .felt-oval { inset: 0; border-radius: 20px; }
.track { position: absolute; inset: 0; pointer-events: none; }
.track-line { fill: none; stroke: rgba(245, 200, 66, 0.35); stroke-width: 1.5; stroke-dasharray: 3 7; }
.track-arrow { fill: var(--gold); }
.ring-slot { position: absolute; z-index: 1; }
.middle { position: absolute; z-index: 1; transform: translate(-50%, -50%); }
.table--rail .middle { position: relative; inset: auto; transform: none; display: flex; justify-content: center; align-items: center; height: 100%; }
.overlay { position: absolute; inset: 0; z-index: 3; display: flex; align-items: center; justify-content: center; pointer-events: none; }
.overlay > :deep(*) { pointer-events: auto; }

.rail { display: flex; flex-direction: column; gap: var(--space-2); flex: none; }
.rail-head { display: flex; align-items: center; justify-content: space-between; gap: var(--space-2); }
.rail-title { font-size: 0.75rem; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: rgba(255, 255, 255, 0.7); }
.rail-reversed { font-size: 0.75rem; font-weight: 700; color: var(--gold); }
.rail-list { margin: 0; padding: 0; list-style: none; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-2); }
.rail-item { min-width: 0; }
.table--rail .felt { flex: 1 1 0; min-height: 120px; }
</style>
