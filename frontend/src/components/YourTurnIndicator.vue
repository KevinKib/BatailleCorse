<script setup lang="ts">
import { useI18n } from '../composables/useI18n';

// The "Your turn" cue shared by the Bataille Corse and Bullshit tables, so both say it the same way.
// The region is always in the page and only its text comes and goes: a live region that is added
// together with its text is not announced, whereas text inserted into an existing one is read once.
// The table decides when it is active and places it with its own class (this one only has a look).
defineProps<{ active: boolean }>();
const ui = useI18n().turn;
</script>

<template>
  <div class="turn-indicator" data-test="turn-indicator" role="status">
    <Transition name="turn-fade">
      <span v-if="active" class="turn-hint" data-test="turn-hint">
        <span class="turn-hint__dot" aria-hidden="true"></span>{{ ui.yourTurn }}
      </span>
    </Transition>
  </div>
</template>

<style scoped>
.turn-indicator { display: flex; justify-content: center; }

.turn-hint {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  white-space: nowrap;
  font-family: var(--font-title);
  font-size: 0.82rem;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: rgb(var(--accent-active-rgb));
}

.turn-hint__dot {
  flex: none;
  width: var(--turn-dot-size);
  height: var(--turn-dot-size);
  border-radius: 50%;
  background: var(--gold);
}

.turn-fade-enter-active,
.turn-fade-leave-active { transition: opacity 0.25s ease; }
.turn-fade-enter-from,
.turn-fade-leave-to { opacity: 0; }

@media (prefers-reduced-motion: reduce) {
  .turn-fade-enter-active,
  .turn-fade-leave-active { transition: none; }
}
</style>
