// A three-round President match (length rounds-3) played to the end: the browser is the host (seat 0), two scripted
// players and a scripted twin of the browser's own seat play by themselves (lowest legal set, else pass). The spec
// presses Ready in the browser at each round-over sheet, checks the sheet and the Scores panel, then the end
// screen with the final standings, and Play again back to the lobby. Everything is waited on through DOM states.
import { hostLobby, joinGuests, noHorizontalScroll } from '../support/presidentRoom';
import { autoplay, connectMyself } from '../support/presidentAutoplay';

[375, 1280].forEach((width) => {
  describe(`President match to the end at ${width} px`, () => {
    it('plays three rounds, shows each round summary and the end screen, then Play again opens the lobby', () => {
      cy.viewport(width, width === 375 ? 667 : 800);
      hostLobby('Alice').then((id) => {
        cy.get('[data-test="customise"]').click();
        cy.get('[data-test="option-length-rounds-3"]').check({ force: true });
        cy.get('[data-test="option-length-rounds-3"]').should('be.checked');
        // The scripted twin of the browser's seat joins the channel in the lobby, so it sees every state from the start.
        connectMyself(id).then((me) => autoplay(id, me, { ready: false }));
        joinGuests(id, ['Bob', 'Carol']).then(([bob, carol]) => {
          autoplay(id, bob);
          autoplay(id, carol);
          cy.get('[data-test="start"]').should('not.be.disabled').click();
          cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
          cy.get('[data-test="round"]').should('have.text', 'Round 1 of 3');

          // The Scores button opens the standings at any time (all at zero before the first round).
          cy.get('[data-test="scores"]').click();
          cy.get('[data-test^="final-row-"]').should('have.length', 3);
          cy.get('[data-test="scores-close"]').click();

          [1, 2].forEach((round) => {
            cy.get('[data-test="round-over-title"]', { timeout: 60000 }).should('have.text', `Round ${round} is over`);
            cy.get('[data-test="round-over-place"]').should('have.length', 3).each(($row, index) => {
              expect($row.find('[data-test="place-label"]').text(), 'place').to.match(new RegExp(`^${index + 1}\\. `));
              expect($row.find('[data-test="place-title"]').text(), 'title').to.not.eq('');
              expect($row.find('[data-test="place-points"]').text(), 'points').to.match(/^\+\d+$/);
            });
            // From round 2 on, every seat shows how its title moved.
            cy.get('[data-test="place-change"]').should(round === 1 ? 'not.exist' : 'have.length', 3);
            noHorizontalScroll();
            cy.get('[data-test="ready"]').click();
            cy.get('[data-test="round"]', { timeout: 30000 }).should('have.text', `Round ${round + 1} of 3`);
          });

          // The last round ends the match.
          cy.get('[data-cy="end-overlay"]', { timeout: 60000 }).should('be.visible');
          cy.get('[data-test="end-winner"]').invoke('text').should('match', /^Winners?: /);
          cy.get('[data-test="end-sub"]').invoke('text').should('match', /^\d+ points$/);
          cy.get('[data-test="end-extra"] [data-test^="final-row-"]').should('have.length', 3);
          noHorizontalScroll();

          cy.get('[data-cy="play-again"]').click();
          cy.get('[data-test="lobby"]', { timeout: 15000 }).should('be.visible');
          cy.then(() => { bob.disconnect(); carol.disconnect(); });
        });
      });
    });
  });
});
