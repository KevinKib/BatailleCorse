<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useBullshitStore } from '../../state/Bullshit.store';
import { CLAIM_MODES, DEFAULT_CLAIM_MODE, type ClaimMode } from '../../model/bullshit/claimMode';
import { useI18n } from '../../composables/useI18n';

const ui = useI18n().bullshitUi.start;
const route = useRoute();
const router = useRouter();
const store = useBullshitStore();

const name = ref('');
const claimMode = ref<ClaimMode>(DEFAULT_CLAIM_MODE);
const joinId = ref((route.params.id as string) ?? '');
const isJoin = ref(route.name === 'bullshit-join');

watch(() => store.gameId, (id) => {
  if (id) router.push(`/games/bullshit/room/${id}`);
});

function onCreate() {
  store.create(name.value || undefined, claimMode.value);
}

async function onJoin() {
  await store.join(joinId.value, name.value || undefined);
  router.push(`/games/bullshit/room/${joinId.value}`);
}
</script>

<template>
  <div class="start">
    <h1>{{ ui.title }}</h1>
    <label>{{ ui.yourName }} <input v-model="name" type="text" /></label>

    <template v-if="!isJoin">
      <fieldset class="claim-mode">
        <legend>{{ ui.claimModeLegend }}</legend>
        <label v-for="mode in CLAIM_MODES" :key="mode">
          <input v-model="claimMode" type="radio" name="claimMode" :value="mode" />
          {{ ui.claimModes[mode] }}
        </label>
      </fieldset>
      <button type="button" class="btn primary" @click="onCreate">{{ ui.createGame }}</button>
    </template>
    <template v-else>
      <label>{{ ui.gameId }} <input v-model="joinId" type="text" /></label>
      <button type="button" class="btn primary" :disabled="!joinId" @click="onJoin">{{ ui.joinGame }}</button>
    </template>
  </div>
</template>

<style scoped>
.start { display: flex; flex-direction: column; gap: 1rem; padding: 2rem; max-width: 28rem; margin: 0 auto; }
.claim-mode { display: flex; flex-direction: column; gap: 0.4rem; border: 1px solid var(--p-primary-color); border-radius: 0.5rem; padding: 0.75rem 1rem; }
.claim-mode legend { padding: 0 0.4rem; }
.claim-mode label { display: flex; align-items: center; gap: 0.5rem; cursor: pointer; }
.btn { padding: 0.6rem 1.4rem; border-radius: var(--button-radius); border: 1px solid var(--p-primary-color); font-size: 1rem; cursor: pointer; }
.btn.primary { background: var(--p-primary-color); color: var(--p-primary-contrast-color, #fff); }
.btn:disabled { opacity: 0.4; cursor: not-allowed; }
</style>
