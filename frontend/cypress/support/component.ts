// Component-test support: `mountApp` mounts the real App (global CSS, felt, route fade) with Pinia, PrimeVue, the router and
// the English messages, so a screen is laid out like in the browser. The app has no i18n plugin: the messages come from the
// `useI18n` composable, which needs nothing installed. No backend: `fetch` never answers, the store is fed by hand.
import { mount } from 'cypress/vue';
import { createPinia, setActivePinia, type Pinia } from 'pinia';
import PrimeVue from 'primevue/config';
import { createMemoryHistory } from 'vue-router';
import App from '../../src/App.vue';
import { createAppRouter } from '../../src/router';
import { primeVueConfig } from '../../src/primeVueConfig';
import '../../src/fonts.css';

export interface MountedApp { pinia: Pinia }

/** Mounts the app on `path` with a fresh Pinia; `prepare` runs first (seed stores and localStorage). */
export function mountApp(path: string, prepare: (pinia: Pinia) => void = () => {}): Cypress.Chainable<MountedApp> {
  const pinia = createPinia();
  setActivePinia(pinia);
  cy.window({ log: false }).then((win) => {
    win.document.documentElement.classList.add('my-app-dark'); // the class index.html puts on <html>
    cy.stub(win, 'fetch').returns(new Promise(() => {}));
  });
  prepare(pinia);
  const router = createAppRouter(createMemoryHistory());
  return cy.then(() => router.push(path).then(() => router.isReady()))
    .then(() => mount(App, { global: { plugins: [pinia, router, [PrimeVue, primeVueConfig]] } }))
    .then(() => ({ pinia }));
}
