<template>
  <button
    type="button"
    class="rules-toggle"
    :aria-expanded="isOpen"
    aria-controls="rules-panel"
    data-cy="rules-toggle"
    @click="toggle"
  >
    <i class="pi pi-info-circle" />
    <span>{{ rules.toggleLabel }}</span>
  </button>

  <div
    v-show="isOpen"
    id="rules-panel"
    class="rules-panel"
    role="dialog"
    :aria-label="rules.panelTitle"
    data-cy="rules-panel"
  >
    <div class="rules-panel__header">
      <h2 class="rules-panel__title">{{ rules.panelTitle }}</h2>
      <button
        type="button"
        class="rules-panel__close"
        :aria-label="rules.closeLabel"
        data-cy="rules-close"
        @click="close"
      >
        <i class="pi pi-times" />
      </button>
    </div>

    <div class="rules-panel__body">
      <template v-for="section in rules.sections" :key="section.title">
        <section v-if="section.kind === 'text'" class="rules-section">
          <h3 class="rules-section__title">{{ section.title }}</h3>
          <p
            v-for="line in section.body"
            :key="line"
            class="rules-section__line"
          >{{ line }}</p>
        </section>

        <section
          v-else-if="section.kind === 'cycle'"
          class="rules-section rules-cycle"
          data-cy="rules-cycle"
        >
          <h3 class="rules-section__title">{{ section.title }}</h3>
          <div class="cycle-row" :class="{ 'cycle-row--loops': section.loops }">
            <template v-for="(step, i) in section.steps" :key="i">
              <span v-if="i > 0" class="cycle-arrow">→</span>
              <span class="value-token value-token--rank">
                <span class="value-token__rank">{{ step }}</span>
              </span>
            </template>
            <span v-if="section.loops" class="cycle-loop" aria-hidden="true">
              <i class="pi pi-replay" />
            </span>
          </div>
          <p class="rules-section__line">{{ section.caption }}</p>
          <p v-if="section.note" class="rules-section__line rules-cycle__note">{{ section.note }}</p>
        </section>

        <section
          v-else-if="section.kind === 'branch'"
          class="rules-section rules-branch"
          data-cy="rules-branch"
        >
          <h3 class="rules-section__title">{{ section.title }}</h3>
          <p class="rules-section__line">{{ section.intro }}</p>
          <div
            v-for="(outcome, i) in section.outcomes"
            :key="i"
            class="branch-outcome"
            :class="`branch-outcome--${outcome.tone}`"
          >
            <i
              class="branch-outcome__icon"
              :class="outcome.tone === 'positive' ? 'pi pi-check-circle' : 'pi pi-times-circle'"
            />
            <span class="branch-outcome__text">
              <span class="branch-outcome__condition">{{ outcome.condition }}</span>
              <span class="branch-outcome__result">{{ outcome.result }}</span>
            </span>
          </div>
        </section>

        <section v-else class="rules-section rules-slap" data-cy="rules-slap">
          <h3 class="rules-section__title">{{ section.title }}</h3>
          <div
            v-for="example in SLAP_EXAMPLES"
            :key="example.key"
            class="slap-rule"
          >
            <span class="slap-rule__label">{{ section.labels[example.key] }}</span>
            <span class="slap-rule__cards">
              <template v-for="(card, i) in example.cards" :key="i">
                <span v-if="example.plus && i > 0" class="slap-rule__plus">+</span>
                <span
                  class="value-token"
                  :class="{ 'value-token--red': isRedSuit(card.suit) }"
                >
                  <span class="value-token__rank">{{ card.rank }}</span>
                  <span class="value-token__suit">{{ suitGlyph(card.suit) }}</span>
                </span>
              </template>
            </span>
          </div>
          <p class="rules-section__line rules-slap__footer">{{ section.footer }}</p>
        </section>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue';
import type { RulesMessages } from '../locales/Messages';
import { useDisclosure } from '../composables/useDisclosure';
import { SLAP_EXAMPLES } from './slapExamples';

// Rules content is passed in so the same panel serves every game (BatailleCorse,
// Bullshit, ...) — single source of truth for the chrome, per-game content.
const props = defineProps<{ rules: RulesMessages }>();
const { isOpen, close, toggle } = useDisclosure();

const SUIT_GLYPH: Record<string, string> = {
  spade: '♠',
  heart: '♥',
  diamond: '♦',
  club: '♣',
};

function suitGlyph(suit: string): string {
  return SUIT_GLYPH[suit] ?? '';
}

function isRedSuit(suit: string): boolean {
  return suit === 'heart' || suit === 'diamond';
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && isOpen.value) close();
}

onMounted(() => document.addEventListener('keydown', handleKeydown));
onBeforeUnmount(() => document.removeEventListener('keydown', handleKeydown));
</script>

<style scoped>
/* Toggle chip — top-right, themed to match the felt table. Sits above the
   cards (z 1000/1001) but below the waiting/end overlays (z 2000). */
.rules-toggle {
  position: absolute;
  top: calc(16px + env(safe-area-inset-top, 0px));
  right: calc(16px + env(safe-area-inset-right, 0px));
  z-index: 1500;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 999px;
  padding: 6px 14px;
  cursor: pointer;
}

.rules-toggle:hover {
  background: rgba(0, 0, 0, 0.6);
}

.rules-panel {
  position: absolute;
  top: 56px;
  right: 16px;
  z-index: 1500;
  width: min(360px, calc(100vw - 32px));
  max-height: 70vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: rgba(0, 0, 0, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 14px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}

.rules-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.rules-panel__title {
  font-family: "Gabarito", sans-serif;
  font-size: 1.1rem;
  font-weight: 700;
  color: #f5c842;
  margin: 0;
}

.rules-panel__close {
  background: transparent;
  border: none;
  color: rgba(255, 255, 255, 0.7);
  font-size: 1rem;
  cursor: pointer;
  padding: 4px;
  line-height: 1;
}

.rules-panel__close:hover {
  color: #fff;
}

.rules-panel__body {
  overflow-y: auto;
  padding: 8px 16px 16px;
}

.rules-section {
  margin-top: 14px;
}

.rules-section__title {
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.55);
  margin: 0 0 4px;
}

.rules-section__line {
  font-size: 0.9rem;
  line-height: 1.4;
  color: rgba(255, 255, 255, 0.88);
  margin: 0 0 4px;
}

/* Slap rules — the most important rules, given prominent card examples. */
.rules-slap {
  padding: 10px 12px;
  background: rgba(245, 200, 66, 0.07);
  border: 1px solid rgba(245, 200, 66, 0.28);
  border-radius: 12px;
}

.slap-rule {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 8px;
}

.slap-rule__label {
  font-size: 0.92rem;
  font-weight: 700;
  color: #f5c842;
}

.slap-rule__cards {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
}

/* Value token — a compact card-corner style chip that keeps the rank big and
   legible (the full card art is illegible at this size). Suit is decorative;
   slap rules are purely rank-based. */
.value-token {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 26px;
  padding: 3px 5px;
  background: #f4efe6;
  color: #1a1a1a;
  border: 1px solid rgba(0, 0, 0, 0.35);
  border-radius: 5px;
  line-height: 1;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.4);
}

.value-token--red {
  color: #c0392b;
}

.value-token__rank {
  font-size: 1.05rem;
  font-weight: 800;
}

.value-token__suit {
  font-size: 0.7rem;
  margin-top: 1px;
}

.slap-rule__plus {
  color: rgba(255, 255, 255, 0.75);
  font-weight: 700;
  margin: 0 2px;
}

.rules-slap__footer {
  margin-top: 10px;
  font-style: italic;
  color: rgba(255, 255, 255, 0.72);
}

/* Cycle — the auto-advancing claim, shown as rank chips with arrows and a
   wrap-around loop badge so the "you don't pick it" mechanic reads at a glance. */
.rules-cycle {
  padding: 10px 12px;
  background: rgba(245, 200, 66, 0.07);
  border: 1px solid rgba(245, 200, 66, 0.28);
  border-radius: 12px;
}

.cycle-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  margin: 4px 0 10px;
}

/* Rank-only chip: reuses the value-token look but drops the suit row. */
.value-token--rank {
  min-width: 24px;
  padding: 4px 6px;
}

.cycle-arrow {
  color: rgba(255, 255, 255, 0.55);
  font-weight: 700;
}

.cycle-loop {
  display: inline-flex;
  align-items: center;
  margin-left: 4px;
  color: #f5c842;
  font-size: 0.95rem;
}

.rules-cycle__note {
  font-style: italic;
  color: rgba(255, 255, 255, 0.62);
}

/* Branch — the two Call-Bullshit outcomes, each a coloured row with a check or
   cross so the consequence of a true vs. false claim is immediately legible. */
.branch-outcome {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid transparent;
}

.branch-outcome--positive {
  background: rgba(82, 196, 128, 0.1);
  border-color: rgba(82, 196, 128, 0.3);
}

.branch-outcome--negative {
  background: rgba(224, 90, 90, 0.1);
  border-color: rgba(224, 90, 90, 0.3);
}

.branch-outcome__icon {
  font-size: 1rem;
  line-height: 1.3;
  flex-shrink: 0;
}

.branch-outcome--positive .branch-outcome__icon {
  color: #6fdba0;
}

.branch-outcome--negative .branch-outcome__icon {
  color: #ec7a7a;
}

.branch-outcome__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.branch-outcome__condition {
  font-size: 0.9rem;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.92);
}

.branch-outcome__result {
  font-size: 0.86rem;
  line-height: 1.35;
  color: rgba(255, 255, 255, 0.78);
}
</style>
