<template>
  <h1>Bataille Corse</h1>


  <Card class="card">
    <template #title>Player 1</template>
    <template #content>
      

      <Button type="submit" severity="primary" label="Send" rounded class="action" 
        @click="send(0)"/>
      <Button type="submit" severity="danger" label="Hit" rounded class="action"
        @click="slap(0)"/>
      <Button type="submit" severity="info" label="Grab" rounded class="action"
        @click="grab(0)"/>

      Cards : {{ batailleCorse.state?.players.at(0)?.nbCards }}
    </template>
  </Card>

  <Card class="card">
    <template #title>Player 2</template>
    <template #content>


      <Button type="submit" severity="primary" label="Send" rounded class="action" 
        @click="send(1)"/>
      <Button type="submit" severity="danger" label="Hit" rounded class="action"
        @click="slap(1)"/>
      <Button type="submit" severity="info" label="Grab" rounded class="action"
        @click="grab(1)"/>

      Cards : {{ batailleCorse.state?.players.at(1)?.nbCards }}
    </template>
  </Card>

  <Card class="card">

    <template #title>Pile</template>
    <template #content>
      Top card: {{ batailleCorse.state?.pile.at(0)?.rank }}
      <br />
      Nb cards: {{ batailleCorse.state?.pile.length }}

      <PlayingCard :size="400" name="card_back"/>
    </template>
  </Card>

  <Button type="submit" label="Create game" rounded class="action" @click="create()"/>

  

</template>

<script setup lang="ts">
import { Button } from 'primevue';
import Card from 'primevue/card';
import { useBatailleCorseStore as useBatailleCorseStore } from '../../state/BatailleCorse.store';
import PlayingCard from './PlayingCard.vue';

const batailleCorse = useBatailleCorseStore();

function slap(playerIndex) {
  batailleCorse.slap(playerIndex);
}

function send(playerIndex) {
  batailleCorse.send(playerIndex);
}

function grab(playerIndex) {
  batailleCorse.grab(playerIndex);
}

function create() {
  batailleCorse.create();
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