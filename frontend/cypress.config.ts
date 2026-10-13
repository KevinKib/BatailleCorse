import { defineConfig } from 'cypress';
import { keepAwakeDuringRun } from './cypress/keepAwake';

export default defineConfig({
  e2e: {
    baseUrl: 'http://localhost:5173',
    specPattern: 'cypress/specs/**/*.cy.ts',
    setupNodeEvents(on) {
      keepAwakeDuringRun(on);
    },
  },
});
