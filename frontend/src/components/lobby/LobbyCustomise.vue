<script setup lang="ts">
import { ref, watch } from 'vue';
import { useI18n } from '../../composables/useI18n';

// The "Customise" disclosure shared by the lobbies that keep some options out of sight by default: a toggle and the
// panel it opens (the slot). Closed at first; `reveal` opens it on mount and whenever it turns true, for a panel that
// holds something non-default every player must be told about. Closing it by hand stays possible.
const props = withDefaults(defineProps<{
  /** Id of the panel, referenced by the toggle's aria-controls. */
  panelId: string;
  reveal?: boolean;
}>(), { reveal: false });

const ui = useI18n().lobby;
const open = ref(props.reveal);
watch(() => props.reveal, (now) => { if (now) open.value = true; });
</script>

<template>
  <button type="button" class="customise" data-test="customise" :aria-expanded="open"
          :aria-controls="panelId" @click="open = !open">
    <i :class="open ? 'pi pi-chevron-up' : 'pi pi-chevron-down'" aria-hidden="true"></i>
    {{ ui.customise }}
  </button>
  <div v-if="open" :id="panelId" class="customise-panel">
    <slot />
  </div>
</template>

<style scoped>
/* Each option of the panel is its own setting: a gap keeps them apart (the Private game option adds a divider). */
.customise-panel { display: flex; flex-direction: column; gap: var(--space-4); }
.customise {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 44px;
  padding: 4px 12px;
  font: inherit;
  font-size: 0.875rem;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  cursor: pointer;
}
.customise:focus-visible { outline: 2px solid rgba(232, 201, 109, 0.7); outline-offset: 1px; }
</style>
