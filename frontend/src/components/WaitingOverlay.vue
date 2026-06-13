<template>
  <div class="waiting-overlay">
    <div class="waiting-card">
      <h2 class="waiting-title">Waiting for opponent…</h2>
      <p class="waiting-sub">Share this link to invite a player</p>
      <div class="share-row">
        <InputText :value="shareLink" readonly class="share-input" />
        <Button label="Copy" icon="pi pi-copy" rounded @click="copyShareLink" />
      </div>
      <p v-if="copied" class="waiting-copied">Copied!</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Button, InputText } from 'primevue';

const route = useRoute();
const router = useRouter();

const shareLink = computed(() => {
  const { href } = router.resolve({ name: 'join', params: { id: route.params.id } });
  return `${window.location.origin}${href}`;
});

const copied = ref(false);
async function copyShareLink() {
  await navigator.clipboard.writeText(shareLink.value);
  copied.value = true;
  setTimeout(() => { copied.value = false; }, 1500);
}
</script>

<style scoped>
.waiting-overlay {
  position: absolute;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.78);
  backdrop-filter: blur(3px);
}

.waiting-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  box-shadow: var(--panel-shadow);
  border-radius: 16px;
  padding: 36px 40px;
  max-width: 460px;
}

.waiting-title {
  font-family: "Gabarito", sans-serif;
  font-size: 1.6rem;
  font-weight: 700;
  color: var(--gold);
  margin: 0;
}

.waiting-sub {
  font-size: 0.72rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.5);
  margin: 0;
}

.share-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.share-input {
  flex: 1;
}

.waiting-copied {
  font-size: 0.72rem;
  color: #4ade80;
  margin: 0;
}
</style>
