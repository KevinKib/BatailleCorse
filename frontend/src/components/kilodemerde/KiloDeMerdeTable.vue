<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import KiloDeMerdeKiloPopover from './KiloDeMerdeKiloPopover.vue';
import KiloDeMerdePile from './KiloDeMerdePile.vue';
import KiloDeMerdeSeatPlate from './KiloDeMerdeSeatPlate.vue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import {
  centerBox, chooseLayout, directionArrows, plateSizeFor, radiiFor, ringCenters, trackRadii, type Frame,
} from '../../model/table/ringLayout';
import { seatsAtTable } from '../../model/kilodemerde/seatOrder';
import type { Countdown } from '../../model/kilodemerde/countdown';
import type { KiloDeMerdePlayer, KiloDeMerdeSeatHand } from '../../model/kilodemerde/KiloDeMerdeState';

// The Kilo de merde table: every seat on the ring of the shared table geometry (`model/table/ringLayout`), me at the
// bottom and my left neighbour (who receives my card) next on screen; the kilo pile, the one slap target, in the middle
// with the round line and the pass countdown. Where the felt is too small to keep 8 px between the plates (a phone with
// ten seats), the seats go to a rail above the middle block. The felt is measured; the model decides.
const props = defineProps<{
  players: KiloDeMerdePlayer[];
  mySeat: number;
  nameOf: (seat: number) => string;
  /** "Round 3 of 10 · pass 7". */
  roundLine: string;
  /** The pass countdown ring; null when no timer runs. */
  countdown: Countdown | null;
  pileSize: number;
  canSlap: boolean;
  /** A race is open and I have not slapped. */
  racing: boolean;
  /** The pass step is open: the seats that chose show a check. */
  ready: boolean;
  /** Every hand, only once the round is over. */
  hands: KiloDeMerdeSeatHand[] | null;
  /** The seats that drew the kilo card of the round. */
  lastSeats: number[];
  disconnected?: (seat: number) => boolean;
  secondsRemaining?: (seat: number) => number | null;
  /** The size of the felt, given instead of measured (tests, previews). */
  frame?: Frame;
}>();
defineEmits<{ slap: [] }>();
const ui = useI18n().kiloDeMerdeUi;

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

const known = computed<Frame | null>(() => props.frame ?? measured.value);
// A phone-sized felt stands in for the first paint.
const FIRST_PAINT: Frame = { width: 344, height: 365 };
const frame = computed<Frame>(() => known.value ?? FIRST_PAINT);

const ring = computed(() => seatsAtTable(props.players, props.mySeat));
const layout = computed(() => chooseLayout(ring.value.length, known.value));
const plate = computed(() => plateSizeFor(frame.value.width));
const compact = computed(() => plate.value.w < 100);
const nameOfSeat = (seat: number) => seat === props.mySeat ? ui.you : props.nameOf(seat);
const handOf = (seat: number) => props.hands?.find(h => h.seat === seat)?.cards ?? null;

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
const arrows = computed(() => known.value ? directionArrows(known.value, false) : []);
const track = computed(() => trackRadii(frame.value));
const middleStyle = computed(() => {
  const box = centerBox(frame.value);
  return { left: '50%', top: `${(box.y + box.h / 2) / frame.value.height * 100}%` };
});

// The kilo cards of the plate that was tapped.
const kiloSeat = ref<number | null>(null);
const kiloPlayer = computed(() => props.players.find(p => p.seat === kiloSeat.value) ?? null);

// The ring of the countdown: 2 * pi * 16.
const CIRCUMFERENCE = 100.5;
const dash = computed(() => `${(props.countdown?.fraction ?? 1) * CIRCUMFERENCE} ${CIRCUMFERENCE}`);
</script>

<template>
  <div class="table" :class="`table--${layout}`" data-test="kilo-table" :data-layout="layout"
       role="region" :aria-label="ui.table.opponents">
    <ul v-if="layout === 'rail'" class="rail" data-test="rail">
      <li v-for="p in ring" :key="p.seat" class="rail-item" :data-seat="p.seat">
        <KiloDeMerdeSeatPlate
          compact
          :player="p"
          :label="nameOfSeat(p.seat)"
          :mine="p.seat === mySeat"
          :ready="ready && p.chosen"
          :last="lastSeats.includes(p.seat)"
          :hand="handOf(p.seat)"
          :disconnected="disconnected?.(p.seat)"
          :seconds-remaining="secondsRemaining?.(p.seat) ?? null"
          @show-kilos="kiloSeat = p.seat" />
      </li>
    </ul>

    <div ref="felt" class="felt">
      <div class="felt-oval" aria-hidden="true"></div>

      <template v-if="layout === 'ring'">
        <svg v-if="known" class="track" :width="known.width" :height="known.height" :viewBox="`0 0 ${known.width} ${known.height}`"
             aria-hidden="true">
          <ellipse :cx="known.width / 2" :cy="known.height / 2" :rx="track.rx" :ry="track.ry" class="track-line" />
          <polygon v-for="a in arrows" :key="a.angle" :points="a.points" class="track-arrow" />
        </svg>
        <div v-for="p in plates" :key="p.player.seat" class="ring-slot" :style="p.style" :data-seat="p.player.seat">
          <KiloDeMerdeSeatPlate
            :player="p.player"
            :label="nameOfSeat(p.player.seat)"
            :compact="compact"
            :mine="p.player.seat === mySeat"
            :ready="ready && p.player.chosen"
            :last="lastSeats.includes(p.player.seat)"
            :hand="handOf(p.player.seat)"
            :disconnected="disconnected?.(p.player.seat)"
            :seconds-remaining="secondsRemaining?.(p.player.seat) ?? null"
            @show-kilos="kiloSeat = p.player.seat" />
        </div>
      </template>

      <div class="middle" :style="layout === 'ring' ? middleStyle : undefined" data-test="center">
        <p class="round-line" data-test="round-heading">{{ roundLine }}</p>
        <div class="pile-row">
          <KiloDeMerdePile :count="pileSize" :can-slap="canSlap" :racing="racing" @slap="$emit('slap')" />
          <div v-if="countdown" class="countdown" data-test="countdown" role="timer"
               :aria-label="format(ui.table.countdown, { seconds: countdown.seconds })">
            <svg viewBox="0 0 36 36" aria-hidden="true" class="countdown-ring">
              <circle cx="18" cy="18" r="16" class="countdown-track" />
              <circle cx="18" cy="18" r="16" class="countdown-arc" :stroke-dasharray="dash" />
            </svg>
            <span class="countdown-seconds" aria-hidden="true">{{ countdown.seconds }}</span>
          </div>
        </div>
      </div>

      <div class="overlay"><slot name="overlay" /></div>
      <div v-if="kiloPlayer" class="popover-slot">
        <KiloDeMerdeKiloPopover :name="nameOfSeat(kiloPlayer.seat)" :mine="kiloPlayer.seat === mySeat" :cards="kiloPlayer.kiloCards"
                                @close="kiloSeat = null" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.table { display: flex; flex-direction: column; gap: var(--space-2); flex: 1 1 0; min-height: 0; width: 100%; max-width: 1100px; margin: 0 auto; }
.felt { position: relative; flex: 1 1 0; min-height: 0; isolation: isolate; }
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
.middle {
  position: absolute; z-index: 1; transform: translate(-50%, -50%);
  display: flex; flex-direction: column; align-items: center; gap: var(--space-1);
}
.table--rail .middle { position: relative; inset: auto; transform: none; justify-content: center; height: 100%; }
.round-line {
  margin: 0; max-width: 100%; text-align: center; white-space: nowrap;
  font-size: 0.75rem; font-weight: 700; color: rgba(255, 255, 255, 0.85); font-variant-numeric: tabular-nums;
}
.pile-row { display: flex; align-items: center; gap: var(--space-2); }
.countdown { position: relative; width: 40px; height: 40px; flex: none; }
.countdown-ring { position: absolute; inset: 0; width: 100%; height: 100%; transform: rotate(-90deg); }
.countdown-track { fill: none; stroke: rgba(255, 255, 255, 0.18); stroke-width: 3; }
.countdown-arc { fill: none; stroke: var(--gold); stroke-width: 3; stroke-linecap: round; }
.countdown-seconds {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  font-size: 0.875rem; font-weight: 700; font-variant-numeric: tabular-nums;
}
.overlay {
  position: absolute; inset: 0; z-index: 3; display: flex; align-items: flex-start; justify-content: center;
  padding-top: var(--space-2); pointer-events: none;
}
.overlay > :deep(*) { pointer-events: auto; }
.popover-slot { position: absolute; z-index: 4; top: 50%; left: 50%; transform: translate(-50%, -50%); }

.rail { margin: 0; padding: 0; list-style: none; flex: none; display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: var(--space-2); }
.rail-item { min-width: 0; height: 44px; }
.table--rail .felt { flex: 1 1 0; min-height: 140px; }
</style>
