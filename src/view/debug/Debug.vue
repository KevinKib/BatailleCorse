<template>
  <h1>Bataille Corse</h1>

  <Card class="card">
    <template #title>Player 1</template>
    <template #content>
      
      <Button type="submit" severity="primary" label="Send" rounded class="action" 
        @click="send(0)" :disabled="isButtonDisabled(0, 'send')"/>
      <Button type="submit" severity="danger" label="Slap" rounded class="action"
        @click="slap(0)" :disabled="isButtonDisabled(0, 'slap')"/>
      <Button type="submit" severity="info" label="Grab" rounded class="action"
        @click="grab(0)" :disabled="isButtonDisabled(0, 'grab')"/>

      Cards : {{ batailleCorse?.players.at(0)?.nbCards }}
    </template>
  </Card>

  <Card class="card">
    <template #title>Player 2</template>
    <template #content>

      <Button type="submit" severity="primary" label="Send" rounded class="action" 
        @click="send(1)" :disabled="isButtonDisabled(1, 'send')"/>
      <Button type="submit" severity="danger" label="Slap" rounded class="action"
        @click="slap(1)" :disabled="isButtonDisabled(1, 'slap')" />
      <Button type="submit" severity="info" label="Grab" rounded class="action"
        @click="grab(1)" :disabled="isButtonDisabled(1, 'grab')"/>

      Cards : {{ batailleCorse?.players.at(1)?.nbCards }}
    </template>
  </Card>

  <Card class="card">

    <template #title>Pile</template>
    <template #content>
      Top card: {{ batailleCorse?.pile.cards.at(0)?.rank }}
      <br />
      Nb cards: {{ batailleCorse?.pile.cards.length }}

      <PlayingCard 
        :size="120"
        :suit="batailleCorse?.pile.cards.at(0)?.suit"
        :rank="batailleCorse?.pile.cards.at(0)?.rank"/>
    </template>
  </Card>

  <Button type="submit" label="Create game" rounded class="action" @click="create()"/>

</template>

<script setup lang="ts">
import { Button } from 'primevue';
import Card from 'primevue/card';
import { useBatailleCorseStore as useBatailleCorseStore } from '../../state/BatailleCorse.store';
import PlayingCard from './PlayingCard.vue';
import { storeToRefs } from 'pinia';

const batailleCorseStore = useBatailleCorseStore();
const { state: batailleCorse } = storeToRefs(batailleCorseStore);

function slap(playerIndex) {
  batailleCorseStore.slap(playerIndex);
}

function send(playerIndex) {
  batailleCorseStore.send(playerIndex);
}

function grab(playerIndex) {
  batailleCorseStore.grab(playerIndex);
}

function create() {
  batailleCorseStore.create();
}

function isButtonDisabled(playerIndex: number, buttonLabel: string) {
  return !batailleCorse.value?.players.at(playerIndex).availableActions.includes(buttonLabel.toLocaleUpperCase());
}

</script>

<style>
.action {
  width: 100px;
  margin-left: 5px;
  margin-right: 5px;
}

.card {
  width: 500px;
  margin-top: 20px;
  margin-left: 20px;
}

</style>