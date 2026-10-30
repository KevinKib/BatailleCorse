<script setup lang="ts">
import { Button } from 'primevue';
import { useI18n } from '../../composables/useI18n';
import type { BlackjackAction } from '../../model/blackjack/BlackjackState';

// Hit and Stand always, Double and Split only when the server lists them (`availableActions`). 48 px high targets.
const props = defineProps<{ actions: BlackjackAction[]; busy?: boolean; idle?: boolean }>();
const emit = defineEmits<{ hit: []; stand: []; double: []; split: [] }>();
const ui = useI18n().blackjackUi;
// Idle: the dock is permanent: the buttons stay on screen, disabled (the standard disabled state, no glow), while it is
// not my turn, so the bar never moves.
const has = (a: BlackjackAction) => props.idle || props.actions.includes(a);
const off = () => props.busy || props.idle;
</script>

<template>
  <div class="bj-actions" :class="{ 'bj-actions--idle': idle }" :data-test="idle ? 'action-bar-idle' : 'action-bar'" role="group" :aria-label="ui.actions.label" :aria-hidden="idle ? 'true' : undefined">
    <Button v-if="has('HIT')" :data-test="idle ? undefined : 'action-hit'" class="bj-action" :label="ui.actions.hit" :disabled="off()" rounded @click="emit('hit')" />
    <Button v-if="has('STAND')" :data-test="idle ? undefined : 'action-stand'" class="bj-action" :label="ui.actions.stand" :disabled="off()" severity="secondary" rounded @click="emit('stand')" />
    <Button v-if="has('DOUBLE')" :data-test="idle ? undefined : 'action-double'" class="bj-action" :label="ui.actions.double" :disabled="off()" severity="warn" rounded @click="emit('double')" />
    <Button v-if="has('SPLIT')" :data-test="idle ? undefined : 'action-split'" class="bj-action" :label="ui.actions.split" :disabled="off()" severity="info" rounded @click="emit('split')" />
  </div>
</template>

<style scoped>
.bj-actions--idle { opacity: 0.4; pointer-events: none; }
.bj-actions { display: flex; justify-content: center; gap: var(--bj-space-2); width: 100%; }
.bj-actions :deep(.bj-action.p-button) { flex: 1 1 0; min-height: 48px; min-width: 44px; }
</style>
