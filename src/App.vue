<template>
  <h1>Bataille Corse</h1>


  <Card class="card">
    <template #title>Player 1</template>
    <template #content>
      <Button type="submit" severity="primary" label="Send" rounded class="action" 
        onclick=""/>
      <Button type="submit" severity="danger" label="Hit" rounded class="action" />
    </template>
  </Card>

  <Card class="card">
    <template #title>Player 2</template>
    <template #content>
      <Button type="submit" severity="primary" label="Send" rounded class="action" 
        @click="send()"/>
      <Button type="submit" severity="danger" label="Hit" rounded class="action" />
    </template>
  </Card>
  

</template>

<script setup>
import { Button, SelectButton } from 'primevue';
import Card from 'primevue/card';

import batailleCorseService from './service/BatailleCorseService';

function send() {
  
  batailleCorseService.send();

}

</script>

<style>
@import "tailwindcss";
@import "tailwindcss-primeui";

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