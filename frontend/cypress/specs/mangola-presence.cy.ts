// Mangola presence, end to end: an opponent who drops during the placement or during the play shows the shared
// disconnect overlay with its countdown, coming back clears it, and staying away past the grace period (5 s under the
// `test` profile) forfeits. The browser is the host (seat 0, side A, who starts), a scripted STOMP client the guest;
// every wait is on a DOM state, never on a delay.
import { connectScriptedPlayer } from '../support/stompPlayer';
import {
  asHostInPlacement, canSow, pitEl, playFromTwoPerPit, sow,
} from '../support/mangolaPlayer';

const overlay = '[data-cy="disconnect-overlay"]';

describe('Mangola presence (end to end)', () => {
  it('during the placement: the overlay shows the countdown, and the guest coming back clears it', () => {
    asHostInPlacement().then(({ id, bob }) => {
      // The server tracks a seat only once it has announced its presence, as the app does.
      cy.then(() => bob.publish('/app/presence', {}));
      cy.then(() => bob.disconnect());
      cy.get(overlay, { timeout: 15000 }).should('contain.text', 'Bob disconnected');
      cy.get('[data-cy="disconnect-countdown"]').should('contain.text', 'You win in');
      cy.get('[data-test="placement-editor"]').should('be.visible');
      cy.then(() => connectScriptedPlayer(id, bob.token, bob.seat)).then((back) => back.publish('/app/presence', {}));
      cy.get(overlay, { timeout: 15000 }).should('not.exist');
      cy.get('[data-test="placement-editor"]').should('be.visible');
    });
  });

  it('during the placement: a guest who stays away forfeits after the grace period', { defaultCommandTimeout: 30000 }, () => {
    asHostInPlacement().then(({ bob }) => {
      cy.then(() => bob.publish('/app/presence', {}));
      cy.then(() => bob.disconnect());
      cy.get(overlay, { timeout: 15000 }).should('be.visible');
      cy.get('[data-test="end"]', { timeout: 60000 }).should('be.visible');
      cy.get('.end-title').should('have.text', 'VICTORY');
      cy.get('.end-sub').invoke('text').should('match', /^Bob disconnected\.$/);
      cy.get(overlay).should('not.exist');
    });
  });

  it('during the play: the overlay shows, the guest comes back and the game goes on', () => {
    playFromTwoPerPit().then(({ id, bob }) => {
      cy.then(() => bob.publish('/app/presence', {}));
      pitEl(8).click();
      cy.get('[data-test="skip-sowing"]', { timeout: 10000 }).click();
      cy.then(() => bob.disconnect());
      cy.get(overlay, { timeout: 15000 }).should('contain.text', 'Bob disconnected');
      cy.get('[data-cy="disconnect-countdown"]').should('contain.text', 'You win in');
      cy.then(() => connectScriptedPlayer(id, bob.token, bob.seat)).then((back) => {
        back.publish('/app/presence', {});
        cy.get(overlay, { timeout: 15000 }).should('not.exist');
        // The game goes on where it was: the guest answers and it is my turn again.
        sow(back as any, 24);
        canSow(5);
      });
    });
  });

  it('during the play: a guest who stays away forfeits after the grace period', { defaultCommandTimeout: 30000 }, () => {
    playFromTwoPerPit().then(({ bob }) => {
      cy.then(() => bob.publish('/app/presence', {}));
      cy.then(() => bob.disconnect());
      cy.get(overlay, { timeout: 15000 }).should('be.visible');
      cy.get('[data-test="end"]', { timeout: 60000 }).should('be.visible');
      cy.get('.end-title').should('have.text', 'VICTORY');
      cy.get('.end-sub').invoke('text').should('match', /^Bob disconnected\.$/);
    });
  });
});
