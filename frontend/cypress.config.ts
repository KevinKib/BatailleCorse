import { defineConfig } from 'cypress';
import { keepAwakeDuringRun } from './cypress/keepAwake';
import { registerRunLog } from './cypress/runLog';

export default defineConfig({
  // One retry in `cypress run` (the run log lists every retried test), none in `cypress open`.
  retries: { runMode: 1, openMode: 0 },
  e2e: {
    baseUrl: 'http://localhost:5173',
    specPattern: 'cypress/specs/**/*.cy.ts',
    setupNodeEvents(on, config) {
      keepAwakeDuringRun(on);
      registerRunLog(on, config);
    },
  },
  component: {
    devServer: { framework: 'vue', bundler: 'vite' },
    specPattern: 'cypress/component/**/*.cy.ts',
    supportFile: 'cypress/support/component.ts',
    indexHtmlFile: 'cypress/support/component-index.html',
  },
});
