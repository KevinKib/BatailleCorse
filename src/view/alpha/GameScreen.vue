<template>
  <div class="gamescreen flex">

    <div class="gamescreen_top flex">
      <div class="left_side"></div>
      <div class="middle_side">
        <h1 class="player_tag">Computer (Easy)</h1>
        <div class="card">
          <PlayingCard 
            :size="90"
            :hidden="true"
            rank="10"
            suit="spade"
          />
          <div class="card_counter">
            <CardCounter :count="batailleCorse?.players.at(1).nbCards"/>
          </div>
        </div>
      </div>
      <div class="right_side"></div>
      
    </div>

    <div class="gamescreen_middle flex">
      <div class="card">
        <PlayingCard
          ref="pile" 
          :size="125"
          :hidden="false"
          :suit="batailleCorse?.pile.cards.at(0)?.suit"
          :rank="batailleCorse?.pile.cards.at(0)?.rank"
        />
        <!-- <Transition name="card_moving">
          <PlayingCard :v-show="transitionActivated"
            :size="125"
            :hidden="false"
            :suit="batailleCorse?.pile.cards.at(0)?.suit"
            :rank="batailleCorse?.pile.cards.at(0)?.rank"
          />
        </Transition> -->
        <div class="card_counter">
          <CardCounter :count="batailleCorse?.pile.cards.length"/>
        </div>
      </div>
    </div>

    <div class="gamescreen_bottom flex">
      <div class="left_side">
        <RouterLink to="/" class="back_button">
          <Button severity="danger" label="Back" icon="pi pi-undo" variant="" rounded />
        </RouterLink>
      </div>

      <div class="middle_side">
        <div class="card">
          <PlayingCard 
            :size="90"
            :hidden="true"
            rank="10"
            suit="spade"
          />
          <div class="card_counter">
            <CardCounter :count="batailleCorse?.players.at(0).nbCards"/>
          </div>
        </div>
        <h1 class="player_tag">SNP</h1>
        <div class="action_buttons">
          <Button class="action_button" icon="pi pi-arrow-up" severity="success" label="Send" rounded
            @click="send(0)" :disabled="isButtonDisabled(0, 'send')"/>
          <Button class="action_button" icon="pi pi-hammer" severity="warn" label="Slap" rounded
            @click="slap(0)" :disabled="isButtonDisabled(0, 'slap')"/>
        </div>
      </div>

      <div class="right_side"></div>
    </div>
  </div>
  
</template>

<script setup lang="ts">
import PlayingCard from '../../components/PlayingCard.vue';
import CardCounter from '../../components/CardCounter.vue';
import { Button } from 'primevue';
import { storeToRefs } from 'pinia';
import { useBatailleCorseStore } from '../../state/BatailleCorse.store';
import { Action } from '../../service/model/Action';
import { onBeforeUnmount, onMounted, onUnmounted, ref, useTemplateRef } from 'vue';
import { RefSymbol, ShallowRefMarker } from '@vue/reactivity';

const batailleCorseStore = useBatailleCorseStore();
const { state: batailleCorse } = storeToRefs(batailleCorseStore);

const transitionActivated = ref(true);
const pile = useTemplateRef("pile");

setInterval(() => {
  if (pile.value) {
    console.log("getBoundingClientRect", pile.value);
    console.log("getBoundingClientRect2", pile.value.rootCard.getBoundingClientRect());
  }

}, 5000);

onMounted(() => {
  document.addEventListener('keyup', setupHotkeys);
})

onBeforeUnmount(() => {
  document.removeEventListener('keyup', setupHotkeys);
})

function setupHotkeys(event) {
  if (event.key == 'q' || event.key == 'c') {
    send(0);
  }
  if (event.key == 'd' || event.key == ' ') {
    slap(0);
  }
}

function slap(playerIndex) {
  batailleCorseStore.slap(playerIndex);
}

function send(playerIndex) {
  batailleCorseStore.send(playerIndex);

  // TODO: make sure send is successful


}

function isButtonDisabled(playerIndex: number, buttonLabel: Action) {
  return !batailleCorse.value?.players.at(playerIndex).availableActions.includes(buttonLabel.toLocaleUpperCase());
}

</script>

<style>

.gamescreen {
  /* @apply bg-[url("src/resources/background/texture-2391621_1280.jpg")]; */
  /* @apply bg-[url("src/resources/background/engin-akyurt-xwb9RDqZKu8-unsplash_small.jpg")]; */
  /* background: #1B5E20; */
  /* background: #005D5D; */

  background: #005D5D;
  background: radial-gradient(circle, rgba(0, 93, 93, 1) 0%, rgba(13, 66, 28, 1) 150%);

  /* background: radial-gradient(circle, rgba(27, 94, 32, 1) 0%, rgba(56, 142, 60, 1) 100%); */
  background-repeat: repeat;
  background-position: center;
  /* opacity: 50%; */
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.gamescreen_top {
  height: 30%;
  
  .middle_side {
    margin-top: auto;
  }
}

.gamescreen_middle {
  height: 40%;
}

.gamescreen_bottom {
  height: 30%;
  .middle_side {
    margin-bottom: auto;
  }
}

.screen_content {
  opacity: 100%;
}

.card_with_counter {
  display: flex;
  flex-direction: row;
  width: fit-content;
  margin-left: auto;
  margin-right: auto;
  
}

.card {
  position: relative;
  width: fit-content;
  margin-left: auto;
  margin-right: auto;
  margin-top: auto;
  margin-bottom: auto;
}

.card_counter {
  position: absolute;
  bottom: 0;
  left: 100%;
  margin-left: 4px;
}

.player_tag {
  position: relative;
  width: fit-content;
  margin-left: auto;
  margin-right: auto;
  margin-top: 8px;
  margin-bottom: 8px;
}

.action_buttons {
  width: fit-content;
  margin-top: auto;
  margin-bottom: 16px;
  margin-left: auto;
  margin-right: auto;
}

.action_button {
  margin-left: 8px;
  margin-right: 8px;
}

.left_side {
  width: 30%;
  display: flex;
}

.middle_side {
  width: 40%;
}

.right_side {
  width: 30%;
}

.back_button {
  margin-left: 16px;
  margin-right: 16px;
  margin-top: auto;
  margin-bottom: 16px;
}

.move_card_animation {

}

.card_moving-enter-active, .card_moving-leave-active {
  transition: opacity 0.5s ease;
}

.card_moving-enter-from, .card_moving-leave-to {
  opacity: 0;
}


</style>