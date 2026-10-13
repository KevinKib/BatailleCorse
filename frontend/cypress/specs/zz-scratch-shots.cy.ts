import { joinAsScriptedPlayer } from '../support/stompPlayer';
[[375, 667], [1280, 800]].forEach(([w, h]) => {
  it('shots ' + w, () => {
    cy.viewport(w, h);
    cy.visit('/games');
    cy.screenshot('picker-' + w, { capture: 'viewport' });
    cy.visit('/games/three-men-morris');
    cy.get('[data-test="name"]').type('Alice');
    cy.get('[data-test="new-game"]').click();
    cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
    cy.location('pathname').then((p) => joinAsScriptedPlayer(p.split('/room/')[1], 'Bob', 'three-men-morris').then((black) => {
      cy.screenshot('lobby-' + w, { capture: 'viewport' });
      cy.get('[data-test="start"]').should('not.be.disabled').click();
      cy.get('[data-test="turn-hint"]').should('be.visible');
      cy.screenshot('start-' + w, { capture: 'viewport' });
      cy.get('[data-test="point-0"]').click();
      cy.get('[data-test="point-0"]').should('have.attr', 'data-owner', 'white');
      cy.then(() => black.publish('/app/three-men-morris/place', { point: 3 }));
      cy.get('[data-test="point-3"]').should('have.attr', 'data-owner', 'black');
      cy.get('[data-test="turn-hint"]').should('be.visible');
      cy.screenshot('mid-' + w, { capture: 'viewport' });
      cy.get('[data-test="point-1"]').click();
      cy.get('[data-test="point-1"]').should('have.attr', 'data-owner', 'white');
      cy.then(() => black.publish('/app/three-men-morris/place', { point: 4 }));
      cy.get('[data-test="point-4"]').should('have.attr', 'data-owner', 'black');
      cy.get('[data-test="turn-hint"]').should('be.visible');
      cy.get('[data-test="point-2"]').click();
      cy.get('line[data-winning="true"]').should('exist');
      cy.screenshot('line-' + w, { capture: 'viewport' });
      cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
      cy.screenshot('end-' + w, { capture: 'viewport' });
    }));
  });
});
