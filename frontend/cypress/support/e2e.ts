import './commands';

Cypress.on('window:before:load', (win) => {
  win.localStorage.setItem('bc_difficulty', '0');
});
