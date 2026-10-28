<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from '../../composables/useI18n';
import { format, plural } from '../../locales/format';

// One player of the Three Men's Morris table: the name (text only, it is user input), the colour in words
// next to a chip, the pieces still to place as small discs, and a glow while it is that player's turn. Six Men's Morris
// adds the counts: "N on board" and "N lost" (the count turns gold at three pieces left).
const props = defineProps<{
  name: string;
  colour: 'white' | 'black';
  piecesToPlace: number;
  active: boolean;
  marked?: boolean;   // the chip carries the mark of the pieces (refined boards): a ring for White, a small diamond for Black
  onBoard?: number;   // pieces on the board; giving it shows the counts (mill variants only)
  lost?: number;      // pieces taken so far
  warn?: boolean;     // three pieces left: one more loss ends the game
}>();

const ui = useI18n().threeMenMorrisUi;
const colourLabel = computed(() => (props.colour === 'white' ? ui.white : ui.black));
const hasCounts = computed(() => props.onBoard !== undefined);
const label = computed(() => hasCounts.value
  ? format(ui.table.countsTag, {
    name: props.name, colour: colourLabel.value, board: props.onBoard ?? 0, hand: props.piecesToPlace, lost: props.lost ?? 0,
  })
  : format(ui.table.colourTag, { name: props.name, colour: colourLabel.value }));
const reserveLabel = computed(() => plural(ui.table.piecesLeft, props.piecesToPlace));
// A count reads "<b>5</b> on board": the number is bold, the words come from the locale around its {n}.
const countParts = (template: string): [string, string] => {
  const [before, after = ''] = template.split('{n}');
  return [before, after];
};
const onBoardParts = computed(() => countParts(ui.table.onBoard));
const lostParts = computed(() => countParts(ui.table.lost));
</script>

<template>
  <div
    class="morris-tag"
    :class="{ 'morris-tag--active': active }"
    data-test="player-tag"
    :data-colour="colour"
    :data-active="active ? 'true' : 'false'"
    role="group"
    :aria-label="label">
    <span class="tag-chip" data-test="tag-chip" :data-colour="colour" aria-hidden="true">
      <span v-if="marked" class="tag-mark" data-test="tag-mark" :data-mark="colour === 'white' ? 'ring' : 'diamond'"></span>
    </span>
    <span class="tag-name" data-test="tag-name">{{ name }}</span>
    <span class="tag-colour" data-test="tag-colour">{{ colourLabel }}</span>
    <span v-if="!hasCounts || piecesToPlace > 0" class="tag-reserve" data-test="tag-reserve" role="img" :aria-label="reserveLabel">
      <span
        v-for="n in piecesToPlace"
        :key="n"
        class="tag-chip tag-chip--small"
        data-test="reserve-piece"
        :data-colour="colour"></span>
    </span>
    <template v-if="hasCounts">
      <span class="tag-sep" aria-hidden="true"></span>
      <span class="tag-count" data-test="tag-on-board" :data-warn="warn ? 'true' : undefined">{{ onBoardParts[0] }}<b>{{ onBoard }}</b>{{ onBoardParts[1] }}</span>
      <span class="tag-count" data-test="tag-lost">{{ lostParts[0] }}<b>{{ lost ?? 0 }}</b>{{ lostParts[1] }}</span>
    </template>
  </div>
</template>

<style scoped>
.morris-tag {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  max-width: 100%;
  box-sizing: border-box;
  min-height: 36px;
  padding: var(--space-1) 14px;
  border-radius: 999px;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
}
.morris-tag--active {
  color: #ffffff;
  border-color: var(--turn-ring-color);
  box-shadow: var(--turn-ring);
}

.tag-chip {
  position: relative;
  flex: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
}
.tag-chip[data-colour='white'] {
  background: radial-gradient(circle at 35% 30%, #fffdf6 0%, #f4ecd8 55%, #bfae85 100%);
}
.tag-chip[data-colour='black'] {
  background: radial-gradient(circle at 35% 30%, #4a433d 0%, #231f1c 60%, #0d0b0a 100%);
  box-shadow: inset 0 0 0 1.5px rgba(255, 255, 255, 0.4);
}
.tag-chip--small { width: 10px; height: 10px; }
/* The same marks as the pieces, scaled to the chip. */
.tag-mark {
  position: absolute;
  left: 50%;
  top: 50%;
  box-sizing: border-box;
}
.tag-mark[data-mark='ring'] {
  width: 50%;
  height: 50%;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  border: 1.5px solid rgba(120, 96, 44, 0.65);
}
.tag-mark[data-mark='diamond'] {
  width: 31%;
  height: 31%;
  transform: translate(-50%, -50%) rotate(45deg);
  background: rgba(244, 236, 216, 0.8);
}

.tag-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 700;
  font-size: 0.85rem;
  letter-spacing: 0.04em;
}
.tag-colour {
  flex: none;
  font-size: 0.875rem;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.6);
}
.tag-sep {
  flex: none;
  width: 1px;
  height: 16px;
  background: rgba(255, 255, 255, 0.18);
}
.tag-count {
  flex: none;
  font-size: 0.78rem;
  color: rgba(255, 255, 255, 0.75);
  white-space: nowrap;
}
.tag-count b { color: #ffffff; font-size: 0.85rem; }
.tag-count[data-warn='true'],
.tag-count[data-warn='true'] b { color: #f5e29a; }
.tag-reserve {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 10px;
}
</style>
