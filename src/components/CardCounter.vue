<template>
  <div class="counter">
    <p class="count">35</p>
  </div>
</template>

<script setup lang="ts">
</script>

<style>

.counter {
  /* width: auto; */
  height: 32px;
  width: 32px;
  clip-path: circle(16px);
  background-color: white;
  color: black;
  border: 2px solid black;
  border-radius:50%;
}

.count {
  width: fit-content;
  font-weight: bold;
  position: relative;
  left: 50%;
  top: 50%;
  -webkit-transform: translate(-50%, -50%);
  transform: translate(-50%, -50%);
}
</style>