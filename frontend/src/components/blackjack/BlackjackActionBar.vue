<script setup lang="ts">
import { Button } from 'primevue';
import { useI18n } from '../../composables/useI18n';
import type { BlackjackAction } from '../../model/blackjack/BlackjackState';

// Hit and Stand always, Double and Split only when the server lists them (`availableActions`). 48 px high targets.
const props = defineProps<{ actions: BlackjackAction[]; busy?: boolean }>();
const emit = defineEmits<{ hit: []; stand: []; double: []; split: [] }>();
const ui = useI18n().blackjackUi;
const has = (a: BlackjackAction) => props.actions.includes(a);
</script>

<template>
  <div class="bj-actions" data-test="action-bar" role="group" :aria-label="ui.actions.label">
    <Button v-if="has('HIT')" data-test="action-hit" class="bj-action" :label="ui.actions.hit" :disabled="busy" rounded @click="emit('hit')" />
    <Button v-if="has('STAND')" data-test="action-stand" class="bj-action" :label="ui.actions.stand" :disabled="busy" severity="secondary" rounded @click="emit('stand')" />
    <Button v-if="has('DOUBLE')" data-test="action-double" class="bj-action" :label="ui.actions.double" :disabled="busy" severity="warn" rounded @click="emit('double')" />
    <Button v-if="has('SPLIT')" data-test="action-split" class="bj-action" :label="ui.actions.split" :disabled="busy" severity="info" rounded @click="emit('split')" />
  </div>
</template>

<style scoped>
.bj-actions { display: flex; justify-content: center; gap: var(--space-2); width: 100%; }
.bj-actions :deep(.bj-action.p-button) { flex: 1 1 0; min-height: 48px; min-width: 44px; }
</style>
