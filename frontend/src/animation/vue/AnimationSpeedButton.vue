<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { AnimationSpeed } from '../core/speed';
import { format } from '../../locales/format';

// The animation speed button shared by the tables that animate (Mangola, President): three small chevrons lit one
// more at each speed, the speed's name, a 44 px target; a tap asks for the next speed (`useAnimationSpeed`). A change
// is read out by its own live region (the button's own name changes silently): not at first, only after a tap. The
// texts are the game's. Two root nodes (the button, then its live region): a class given to the component goes on
// the button, so a table places it as it likes.
defineOptions({ inheritAttrs: false });
const props = defineProps<{
  speed: AnimationSpeed;
  /** The three speeds' names, shown on the button and read in `label`. */
  names: Record<AnimationSpeed, string>;
  /** The accessible name, with '{speed}'. */
  label: string;
}>();
const emit = defineEmits<{ cycle: [] }>();

const name = computed(() => props.names[props.speed]);
const accessibleName = computed(() => format(props.label, { speed: name.value }));
const announcement = ref('');
watch(() => props.speed, () => { announcement.value = accessibleName.value; });
</script>

<template>
  <button v-bind="$attrs" type="button" class="speed-button" data-test="speed-button" :aria-label="accessibleName"
          @click="emit('cycle')">
    <span class="speed-chevrons" aria-hidden="true" :data-speed="speed"><i></i><i></i><i></i></span>
    <span aria-hidden="true">{{ name }}</span>
  </button>
  <span class="speed-live" role="status" data-test="speed-live">{{ announcement }}</span>
</template>

<style scoped>
.speed-button {
  display: inline-flex;
  align-items: center;
  gap: 0.4em;
  min-width: 44px;
  min-height: 44px;
  padding: 0 var(--space-3, 0.75rem);
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.55);
  color: inherit;
  font: inherit;
  font-size: 0.8rem;
  cursor: pointer;
}
.speed-button:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
.speed-chevrons { display: inline-flex; gap: 2px; }
.speed-chevrons i {
  width: 0.45em;
  height: 0.45em;
  border-top: 2px solid currentColor;
  border-right: 2px solid currentColor;
  transform: rotate(45deg) scale(0.9);
  opacity: 0.25;
}
.speed-chevrons i:nth-child(1) { opacity: 1; }
.speed-chevrons[data-speed='fast'] i:nth-child(-n + 2),
.speed-chevrons[data-speed='instant'] i { opacity: 1; }
.speed-live {
  position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0;
  overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0;
}
</style>
