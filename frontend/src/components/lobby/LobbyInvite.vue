<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue';
import { Button, InputText } from 'primevue';
import { useI18n } from '../../composables/useI18n';

// The invite link and its Copy button. "Copied!" is announced through a polite live region; when the
// clipboard is unavailable (insecure origin) the link is left selected so Ctrl+C works.
const props = defineProps<{
  joinLink: string;
  /** The game is hidden from the public list. */
  privateGame?: boolean;
  /** Show the read-only "Private game" chip (the guests; the host has the switch). */
  showChip?: boolean;
}>();

const ui = useI18n().lobby;
const copied = ref(false);
const linkInput = ref<InstanceType<typeof InputText> | null>(null);
let copiedTimer: ReturnType<typeof setTimeout> | null = null;
onBeforeUnmount(() => { if (copiedTimer) clearTimeout(copiedTimer); });

async function copyLink() {
  try {
    await navigator.clipboard.writeText(props.joinLink);
  } catch {
    (linkInput.value?.$el as HTMLInputElement | undefined)?.select();
    return;
  }
  copied.value = true;
  if (copiedTimer) clearTimeout(copiedTimer);
  copiedTimer = setTimeout(() => { copied.value = false; }, 1500);
}

function selectAll(event: FocusEvent) {
  (event.target as HTMLInputElement).select();
}
</script>

<template>
  <div class="field-group">
    <div class="invite-head">
      <label class="field-label" for="invite-link">{{ ui.inviteLabel }}</label>
      <span v-if="privateGame && showChip" class="private-chip" data-test="private-chip">
        <i class="pi pi-lock" aria-hidden="true"></i>{{ ui.privateChip }}
      </span>
      <p class="copied" data-test="copied" role="status" aria-live="polite">
        <template v-if="copied">{{ ui.copied }}</template>
      </p>
    </div>
    <div class="share-row">
      <InputText
        id="invite-link"
        ref="linkInput"
        :value="joinLink"
        readonly
        class="share-input"
        data-test="invite-link"
        @focus="selectAll" />
      <Button
        data-test="copy-link"
        :label="ui.copy"
        icon="pi pi-copy"
        rounded
        @click="copyLink" />
    </div>
    <p class="invite-hint" data-test="invite-hint">{{ privateGame ? ui.inviteHintPrivate : ui.inviteHintPublic }}</p>
  </div>
</template>

<style scoped>
.field-group { width: 100%; display: flex; flex-direction: column; gap: 8px; }
.field-label {
  font-size: 0.875rem;
  font-weight: 700;
  line-height: 1rem;
  color: rgba(255, 255, 255, 0.6);
  margin: 0;
}
.share-row { display: flex; gap: 8px; width: 100%; }
.share-input { flex: 1; min-width: 0; }
:deep(.share-input.p-inputtext) {
  min-height: 44px;
  background: rgba(0, 0, 0, 0.4);
  border-color: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.9);
  border-radius: 8px;
  font-family: var(--font-ui);
  font-size: 0.875rem;
}
:deep(.share-input.p-inputtext:focus-visible) {
  border-color: rgba(232, 201, 109, 0.6);
  outline: 2px solid rgba(232, 201, 109, 0.5);
  outline-offset: 1px;
}
.share-row :deep(.p-button) { flex: none; min-height: 44px; }
.private-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  font-size: 0.75rem;
  line-height: 1rem;
  color: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 999px;
}
.invite-hint { margin: 0; font-size: 0.8125rem; line-height: 1.2rem; color: rgba(255, 255, 255, 0.6); }
.invite-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
/* "Copied!" shares the label's row so the status line never adds height to the panel. */
.copied { min-height: 16px; margin: 0; font-size: 0.75rem; line-height: 16px; color: #4ade80; }
</style>
