<template>
  <div class="title_screen flex">

    <div class="titlescreen_top flex">
      <div class="m-auto">
        <PlayingCard 
          :size="120"
          :hidden="true"
          rank="10"
          suit="spade"
          />
      </div>
    </div>

    <div class="titlescreen_middle flex">
      <div class="m-auto">
        <PlayingCard 
        :size="120"
        :hidden="false"
        rank="10"
        suit="spade"
        />
        <CardCounter />
      </div>
    </div>

    <div class="titlescreen_bottom flex">
      <div class="m-auto">
        <PlayingCard 
          :size="120"
          :hidden="true"
          rank="10"
          suit="spade"
          />
      </div>

    </div>

  </div>
  
</template>

<script setup lang="ts">
import PlayingCard from '../../components/PlayingCard.vue';
import CardCounter from '../../components/CardCounter.vue';
</script>

<style>

.title_screen {
  @apply bg-[url("src/resources/background/texture-2391621_1280.jpg")];
  /* @apply bg-[url("src/resources/background/engin-akyurt-xwb9RDqZKu8-unsplash_small.jpg")]; */
  background-repeat: repeat;
  background-position: center;
  /* opacity: 50%; */
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.titlescreen_top {
  height: 30%
}

.titlescreen_middle {
  height: 40%
}

.titlescreen_bottom {
  height: 30%
}

.screen_content {
  opacity: 100%;
}
</style>