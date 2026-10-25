<script setup lang="ts">
import TitlePanel from '../components/TitlePanel.vue';
import { useI18n } from '../composables/useI18n';

// A static text page in the frame of the Game home (felt, glass panel): headings, sections, a way back.
const privacy = useI18n().privacy;
</script>

<template>
  <TitlePanel :title="privacy.title">
    <div class="privacy" data-test="privacy-page">
      <p class="intro">{{ privacy.intro }}</p>
      <section v-for="section in privacy.sections" :key="section.id" :aria-labelledby="`privacy-${section.id}`" :data-test="`privacy-${section.id}`">
        <h2 :id="`privacy-${section.id}`">{{ section.heading }}</h2>
        <p v-for="paragraph in section.paragraphs" :key="paragraph">{{ paragraph }}</p>
      </section>
      <RouterLink :to="{ name: 'games' }" class="back-link" data-test="privacy-back">{{ privacy.back }}</RouterLink>
    </div>
  </TitlePanel>
</template>

<style scoped>
/* Room for reading: wider than the Game home panel. */
.title-panel { max-width: 720px; }

.privacy {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 20px;
  text-align: left;
  color: rgba(255, 255, 255, 0.85);
  font-size: 0.9375rem;
  line-height: 1.55;
}
.privacy p { margin: 0; overflow-wrap: anywhere; }
.privacy section { display: flex; flex-direction: column; gap: 8px; }
.privacy h2 {
  margin: 0;
  font-family: var(--font-title);
  font-size: 0.875rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--gold-soft);
}
.back-link {
  align-self: center;
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 0 12px;
  color: var(--gold-soft);
  text-decoration: underline;
}
.back-link:hover { color: var(--gold); }
.back-link:focus-visible { outline: 2px solid var(--gold-soft); outline-offset: 2px; border-radius: 4px; }
</style>
