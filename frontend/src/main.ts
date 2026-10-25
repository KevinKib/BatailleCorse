
import { createApp } from 'vue';
import App from './App.vue';
import PrimeVue from 'primevue/config';
import { primeVueConfig } from './primeVueConfig';

import webSocketService from './service/WebSocketService';
import { createPinia } from 'pinia';

const app = createApp(App);
app.use(PrimeVue, primeVueConfig);

import { createAppRouter } from './router';

const router = createAppRouter();

const pinia = createPinia();
app.use(pinia);
app.use(router);

app.mount('#app');
webSocketService.init();

