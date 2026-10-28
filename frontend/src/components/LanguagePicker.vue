<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { SUPPORTED_LOCALES, type Locale } from '../locales';
import { currentLocale, setLocale } from '../locales/locale';
import { useI18n } from '../composables/useI18n';

// Choosing the language: a globe button with a small menu (`menu`, the game picker and the games with
// no settings gear) or a heading with a segmented row (`row`, inside the settings panel). Each language is
// written in itself, never as a flag. Nothing is rendered until more than one language is offered.
// Switching saves the choice and reloads the page (`setLocale`), so the whole UI boots in the new language.
const props = withDefaults(defineProps<{ variant: 'menu' | 'row'; locales?: readonly Locale[] }>(), {
  locales: () => SUPPORTED_LOCALES,
});
const ui = useI18n().language;
const active = currentLocale();
const offered = computed(() => props.locales.length > 1);

const open = ref(false);
const root = ref<HTMLElement | null>(null);

function choose(locale: Locale) {
  open.value = false;
  if (locale !== active) setLocale(locale);
}

// A press outside the menu closes it, like Escape.
function onPointerDown(e: Event) {
  if (root.value && !root.value.contains(e.target as Node)) open.value = false;
}
watch(open, (isOpen) => {
  if (isOpen) document.addEventListener('pointerdown', onPointerDown);
  else document.removeEventListener('pointerdown', onPointerDown);
});
onBeforeUnmount(() => document.removeEventListener('pointerdown', onPointerDown));
</script>

<template>
  <div v-if="offered && variant === 'menu'" ref="root" class="language-menu" @keydown.esc="open = false">
    <button
      type="button"
      class="globe"
      data-test="language-button"
      :aria-label="ui.label"
      :title="ui.label"
      :aria-expanded="open"
      aria-controls="language-menu"
      @click="open = !open">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
        <circle cx="12" cy="12" r="9" />
        <path d="M3 12h18M12 3c3 3.2 3 14.8 0 18M12 3c-3 3.2-3 14.8 0 18" />
      </svg>
    </button>
    <div v-if="open" id="language-menu" class="menu" data-test="language-menu" role="group" :aria-label="ui.label">
      <button
        v-for="locale in locales"
        :key="locale"
        type="button"
        class="option"
        :class="{ current: locale === active }"
        :data-test="`language-option-${locale}`"
        :lang="locale"
        :aria-pressed="locale === active"
        @click="choose(locale)">
        {{ ui.names[locale] }}
        <span v-if="locale === active" class="tick" aria-hidden="true">✓</span>
      </button>
    </div>
  </div>

  <div v-else-if="offered" class="language-section">
    <div class="heading" data-test="language-heading">{{ ui.label }}</div>
    <div class="segmented" data-test="language-row" role="group" :aria-label="ui.label">
      <button
        v-for="locale in locales"
        :key="locale"
        type="button"
        class="segment"
        :class="{ current: locale === active }"
        :data-test="`language-option-${locale}`"
        :lang="locale"
        :aria-pressed="locale === active"
        @click="choose(locale)">
        {{ ui.names[locale] }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.language-menu { position: relative; display: inline-flex; }
.globe {
  width: 44px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: rgba(255, 255, 255, 0.8);
  cursor: pointer;
}
.globe:hover { background: rgba(255, 255, 255, 0.08); color: #fff; }
.globe:focus-visible, .option:focus-visible, .segment:focus-visible { outline: 2px solid var(--gold-soft); outline-offset: 2px; }

/* Opens right under the globe, never over it; the right edge lines up with the button so it stays on screen. */
.menu {
  position: absolute;
  top: calc(100% + var(--space-1));
  right: 0;
  z-index: 1400;
  width: min(220px, calc(100vw - 16px));
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding: var(--space-2);
  box-sizing: border-box;
  border-radius: 12px;
  background: rgba(20, 24, 22, 0.97);
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.5);
}
.option {
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: 0 var(--space-3);
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #fff;
  font: inherit;
  font-size: 0.875rem;
  cursor: pointer;
}
.option:hover, .option.current { background: rgba(255, 255, 255, 0.08); }
.tick { color: var(--gold); }

/* In the settings panel: a heading, then the languages side by side (the panel's own gap spaces the sections). */
.language-section { display: flex; flex-direction: column; gap: var(--space-1); }
.heading {
  padding: var(--space-1) var(--space-1) 0;
  font-size: 0.6875rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.6);
}
.segmented {
  display: flex;
  gap: 2px;
  padding: 2px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.45);
}
.segment {
  flex: 1 1 0;
  min-width: 0;
  min-height: 44px;
  padding: 0 var(--space-2);
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: rgba(255, 255, 255, 0.75);
  font: inherit;
  font-size: 0.8125rem;
  font-weight: 700;
  cursor: pointer;
}
.segment.current {
  background: rgba(245, 200, 66, 0.18);
  color: var(--gold);
  box-shadow: inset 0 0 0 1px rgba(245, 200, 66, 0.6);
}
</style>
