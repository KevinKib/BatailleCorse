<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from '../../composables/useI18n';
import MorrisMiniBoard from './MorrisMiniBoard.vue';
import { VARIANT_KEYS } from '../../model/threemenmorris/geometries';
import type { MorrisVariantKey } from '../../model/threemenmorris/MorrisState';
import type { OptionDescriptor } from '../../model/lobby/LobbyView';

// The options of a Three Men's Morris lobby: the variant as three cards, each with a mini board that shows its
// geometry, the rule of the chosen one on one line, then the colour of the host. The host edits, everybody else
// reads the same choices. The lobby stays the source of truth: a change is asked for and drawn back on the value
// the next lobby view holds, as in the generic options.
const props = defineProps<{
  descriptors: OptionDescriptor[];
  options: Record<string, string>;
  editable: boolean;
}>();
const emit = defineEmits<{ 'set-option': [key: string, value: string] }>();

const ui = useI18n().morrisLobby;

const descriptorOf = (key: string): OptionDescriptor | undefined => props.descriptors.find(d => d.key === key);
const valueOf = (key: string, fallback: string): string =>
  props.options[key] ?? descriptorOf(key)?.defaultValue ?? fallback;

const variants = computed<MorrisVariantKey[]>(() => {
  const offered = descriptorOf('variant')?.values ?? VARIANT_KEYS;
  return VARIANT_KEYS.filter(key => offered.includes(key));
});
const variant = computed(() => {
  const value = valueOf('variant', 'tapatan') as MorrisVariantKey;
  return VARIANT_KEYS.includes(value) ? value : 'tapatan';
});
type Colour = keyof typeof ui.colours;
const colours = computed(() => (descriptorOf('hostColour')?.values ?? ['white', 'black', 'random']) as Colour[]);
const colour = computed(() => valueOf('hostColour', 'white') as Colour);

function pick(event: Event, key: string, value: string, current: string) {
  emit('set-option', key, value);
  // A refused change leaves the lobby as it was: draw the group back on the value the lobby holds.
  const group = (event.target as HTMLInputElement).closest('fieldset');
  group?.querySelectorAll<HTMLInputElement>('input[type="radio"]').forEach((input) => {
    input.checked = input.value === current;
  });
}
</script>

<template>
  <div class="morris-options" data-test="game-options">
    <fieldset v-if="editable" class="group" data-test="option-variant">
      <legend class="field-label">{{ ui.variantLabel }}</legend>
      <div class="cards">
        <label
          v-for="key in variants"
          :key="key"
          class="card"
          :class="{ 'card--on': variant === key }"
          :data-test="`variant-card-${key}`">
          <input
            type="radio"
            name="option-variant"
            :value="key"
            :checked="variant === key"
            :data-test="`option-variant-${key}`"
            @change="pick($event, 'variant', key, variant)" />
          <MorrisMiniBoard :variant="key" />
          <span class="card-name">{{ ui.variants[key].name }}</span>
          <span class="card-summary">{{ ui.variants[key].summary }}</span>
        </label>
      </div>
    </fieldset>
    <div v-else class="read" data-test="option-variant">
      <span class="field-label">{{ ui.variantLabel }}</span>
      <div class="card card--on card--read" :data-test="`variant-card-${variant}`">
        <MorrisMiniBoard :variant="variant" />
        <span class="card-name">{{ ui.variants[variant].name }}</span>
        <span class="card-summary">{{ ui.variants[variant].summary }}</span>
      </div>
    </div>

    <p class="rule" data-test="variant-rule">{{ ui.variants[variant].rule }}</p>

    <fieldset v-if="editable" class="group" data-test="option-hostColour">
      <legend class="field-label">{{ ui.colourLabel }}</legend>
      <div class="segments">
        <label
          v-for="value in colours"
          :key="value"
          class="segment"
          :class="{ 'segment--on': colour === value }"
          :data-test="`colour-${value}`">
          <input
            type="radio"
            name="option-hostColour"
            :value="value"
            :checked="colour === value"
            :data-test="`option-hostColour-${value}`"
            @change="pick($event, 'hostColour', value, colour)" />
          <span class="chip" :class="`chip--${value}`" aria-hidden="true"></span>
          <span>{{ ui.colours[value] }}</span>
        </label>
      </div>
    </fieldset>
    <div v-else class="read read--colour" data-test="option-hostColour">
      <span class="field-label">{{ ui.hostPlays }}</span>
      <span class="read-value">
        <span class="chip" :class="`chip--${colour}`" aria-hidden="true"></span>
        {{ ui.colours[colour] }}
      </span>
    </div>
  </div>
</template>

<style scoped>
.morris-options { width: 100%; display: flex; flex-direction: column; gap: var(--space-3, 12px); }
.group { min-width: 0; margin: 0; padding: 0; border: 0; display: block; }
.field-label {
  display: block;
  margin: 0 0 var(--space-2, 8px);
  padding: 0;
  font-size: 0.75rem;
  line-height: 1rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.6);
}

/* Three cards on a row, from a phone up: a mini board, the name, one short line. */
.cards { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.card {
  position: relative;
  min-width: 0;
  min-height: 44px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 6px;
  box-sizing: border-box;
  text-align: center;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 8px;
  cursor: pointer;
}
.card--on { color: var(--gold); border-color: var(--gold); background: rgba(232, 201, 109, 0.12); }
.card--read { cursor: default; }
.card-name { font-weight: 700; font-size: 0.875rem; line-height: 1.25rem; }
.card-summary { font-size: 0.75rem; line-height: 1rem; color: rgba(255, 255, 255, 0.75); overflow-wrap: anywhere; }
.card--on .card-summary { color: rgba(255, 255, 255, 0.85); }

.rule { margin: 0; font-size: 0.8125rem; line-height: 1.2rem; color: rgba(255, 255, 255, 0.8); }

.segments { display: flex; gap: 8px; }
.segment {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 4px 8px;
  box-sizing: border-box;
  font-size: 0.8125rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 8px;
  cursor: pointer;
}
.segment--on { color: var(--gold); border-color: var(--gold); background: rgba(232, 201, 109, 0.12); }

/* The colour dot: ivory, ebony, or half of each for a draw at the start. */
.chip {
  flex: none;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1.5px rgba(255, 255, 255, 0.4);
}
.chip--white { background: radial-gradient(circle at 35% 30%, #fffdf6 0%, #f4ecd8 55%, #bfae85 100%); }
.chip--black { background: radial-gradient(circle at 35% 30%, #4a433d 0%, #231f1c 60%, #0d0b0a 100%); }
.chip--random { background: linear-gradient(90deg, #f4ecd8 50%, #231f1c 50%); }

/* The radios stay in the accessibility tree and keep the focus ring, but are drawn by their label. */
.card input,
.segment input { position: absolute; inset: 0; width: 100%; height: 100%; margin: 0; opacity: 0; cursor: pointer; }
.card:has(input:focus-visible),
.segment:has(input:focus-visible) { outline: 2px solid rgba(232, 201, 109, 0.7); outline-offset: 1px; }

.read { display: flex; flex-direction: column; gap: 0; }
.read .card { width: 100%; max-width: 160px; }
.read--colour { min-height: 44px; justify-content: center; padding: 2px 12px; box-sizing: border-box; background: rgba(0, 0, 0, 0.25); border: 1px solid rgba(255, 255, 255, 0.12); border-radius: 8px; }
.read--colour .field-label { margin: 0; }
.read-value { display: inline-flex; align-items: center; gap: 8px; color: var(--gold); font-weight: 600; font-size: 0.875rem; line-height: 1.25rem; }
</style>
