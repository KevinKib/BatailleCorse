<template>
  <div class="forfeit-banner" data-test="forfeit-banner" role="status">{{ label }}</div>
</template>

<script setup lang="ts">
defineProps<{ label: string }>();
</script>

<style scoped>
.forfeit-banner {
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.05em;
  color: #fff;
  background: rgba(0, 0, 0, 0.78);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.7);
  border-radius: 999px;
  padding: 6px 18px;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.5);
}
</style>
