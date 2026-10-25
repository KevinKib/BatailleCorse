// The app is drawn on a dark felt only, so the inherited text must be white whatever the computer's own theme. The
// colour scheme of the browser is emulated through the DevTools protocol (the OS theme of a laptop), once light and
// once dark; both must give the same white text on the lobby, Mangola, President and Kobo (issue: black button text
// on laptops set to a light theme). A dark-theme computer is the reference: nothing may change for it.
import { asHostInPlacement, playFromTwoPerPit } from '../support/mangolaPlayer';
import { hostLobby as presidentLobby, joinGuests as presidentGuests } from '../support/presidentRoom';
import { startKoboTable } from '../support/koboRoom';

const WHITE = 'rgb(255, 255, 255)';

/** Emulates the OS theme (`prefers-color-scheme`) for the whole browser, before the page loads. */
function emulateOsTheme(scheme: 'light' | 'dark') {
  cy.wrap(Cypress.automation('remote:debugger:protocol', {
    command: 'Emulation.setEmulatedMedia',
    params: { features: [{ name: 'prefers-color-scheme', value: scheme }] },
  }), { log: false });
}

const colourOf = (selector: string) => cy.get(selector).first().then(($el) => getComputedStyle($el[0]).color);
function expectWhite(selector: string) {
  colourOf(selector).should('eq', WHITE);
}

/** The page itself: the colour and the scheme that every unstyled text, native control and scrollbar inherits. */
function pageIsWhiteOnDark() {
  expectWhite('body');
  expectWhite('.app-root');
  cy.document().then((doc) => {
    expect(getComputedStyle(doc.documentElement).colorScheme, 'color-scheme of the page').to.eq('dark');
  });
}

(['light', 'dark'] as const).forEach((theme) => {
  describe(`White text under an OS theme set to ${theme}`, () => {
    beforeEach(() => emulateOsTheme(theme));
    // The next specs of the run must not inherit the emulation.
    after(() => emulateOsTheme('dark'));

    it('the lobby shows white text and a dark colour scheme', () => {
      cy.viewport(375, 667);
      cy.visit('/games/mangola');
      cy.get('[data-test="name"]').type('Alice');
      cy.get('[data-test="new-game"]').click();
      cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
      pageIsWhiteOnDark();
      expectWhite('[data-test="lobby"]');
    });

    it('Mangola: the placement editor and its buttons are white', () => {
      cy.viewport(375, 667);
      asHostInPlacement();
      pageIsWhiteOnDark();
      expectWhite('#mg-editor-title');
      expectWhite('[data-test="seeds-left"]');
      ['mode-add', 'mode-remove', 'preset-two-per-pit', 'confirm'].forEach((name) => expectWhite(`[data-test="${name}"]`));
    });

    it('Mangola: the board footer (speed button, legends) is white', () => {
      cy.viewport(375, 667);
      playFromTwoPerPit();
      pageIsWhiteOnDark();
      expectWhite('[data-test="speed-button"]');
      expectWhite('.mg-board__legend--top');
      expectWhite('.mg-board__legend--bottom');
    });

    it('President: the speed button is white', () => {
      cy.viewport(375, 667);
      presidentLobby('Alice').then((id) => {
        presidentGuests(id, ['Bob', 'Carol']).then(() => {
          cy.get('[data-test="start"]').should('not.be.disabled').click();
          cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
          pageIsWhiteOnDark();
          expectWhite('[data-test="speed-button"]');
        });
      });
    });

    it('Kobo: the piles are white', () => {
      cy.viewport(375, 667);
      startKoboTable(2);
      pageIsWhiteOnDark();
      expectWhite('[data-test="draw-pile"]');
      expectWhite('[data-test="discard-pile"]');
    });
  });
});

// Screenshots of docs/screenshots/force-white-text, taken under an OS theme set to light:
// `npx cypress run --env SHOT=before` on the old build, `SHOT=after` on the fix. Skipped otherwise.
const shot = Cypress.env('SHOT') as string | undefined;
(shot ? [[375, 667], [1280, 720]] : []).forEach(([width, height]) => {
  describe(`Screenshots under a light OS theme at ${width}x${height}`, () => {
    beforeEach(() => emulateOsTheme('light'));
    after(() => emulateOsTheme('dark'));

    it('title screen and lobby (PrimeVue Button and InputText)', () => {
      cy.viewport(width, height);
      cy.visit('/games/mangola');
      cy.get('[data-test="name"]').type('Alice');
      cy.wait(800); // the route fade must be over, or the shot catches the empty felt
      cy.screenshot(`${shot}-title-${width}`, { capture: 'viewport' });
      cy.get('[data-test="new-game"]').click();
      cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
      cy.screenshot(`${shot}-lobby-${width}`, { capture: 'viewport' });
    });

    it('Mangola placement', () => {
      cy.viewport(width, height);
      asHostInPlacement();
      cy.screenshot(`${shot}-placement-${width}`, { capture: 'viewport' });
    });

    it('Mangola board', () => {
      cy.viewport(width, height);
      playFromTwoPerPit();
      cy.screenshot(`${shot}-board-${width}`, { capture: 'viewport' });
    });
  });
});
