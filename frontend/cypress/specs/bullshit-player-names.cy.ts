// The names typed at connection time must show up during the game, not "Player N".
const LONG_NAME = 'Bartholomew-Maximilian-Longname';

function createRoom(name: string) {
  // Enter through the menu so the WebSocket is connected before we create (see bullshit-table-and-lobby).
  cy.visit('/games/bataillecorse');
  cy.contains('button', 'Play Bullshit').click();
  cy.url().should('include', '/games/bullshit/create');
  cy.get('[data-test="name"]').type(name);
  // Deliberate fixed wait: a create sent before the WebSocket handshake finishes is reported as an error.
  cy.wait(1500);
  cy.get('[data-test="submit"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/bullshit\/room\/.+/);
  return cy.url().then((url) => url.split('/room/')[1]);
}

function joinAs(id: string, name: string) {
  return cy.request('POST', `/api/bullshit/game/${id}/join`, { name })
    .then((res) => res.body as { playerId: number; token: string });
}

function startGame() {
  cy.get('[data-test="start"]', { timeout: 10000 }).should('not.be.disabled').click();
  cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
}

describe('Bullshit player names in game', () => {
  it('shows the typed names on the table, and again after a page refresh', () => {
    createRoom('Alice').then((id) => {
      joinAs(id, 'Bob');
      joinAs(id, '');   // no name typed: falls back to "Player 3"
      startGame();

      cy.get('[data-test="seat-label"]').then(($labels) => {
        expect([...$labels].map((l) => l.textContent?.trim())).to.deep.eq(['Bob', 'Player 3']);
      });
      cy.contains('.my-tag', 'You').should('be.visible');

      cy.reload();
      cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
      cy.get('[data-test="seat-label"]').then(($labels) => {
        expect([...$labels].map((l) => l.textContent?.trim())).to.deep.eq(['Bob', 'Player 3']);
      });
    });
  });

  it('serves the names to a rejoining player through the REST state', () => {
    createRoom('Alice').then((id) => {
      joinAs(id, 'Bob').then(({ token }) => {
        startGame();
        cy.request(`/api/bullshit/game/${id}?token=${token}`).then((res) => {
          expect(res.body.players.map((p: { name: string }) => p.name)).to.deep.eq(['Alice', 'Bob']);
        });
      });
    });
  });

  it('truncates a very long name on a phone without overflow or scrolling', () => {
    cy.viewport(375, 667);
    createRoom('Alice').then((id) => {
      joinAs(id, LONG_NAME);
      joinAs(id, 'Charlie-Alexander-Longname');
      joinAs(id, 'Dorothea-Wilhelmina-Longname');
      joinAs(id, 'Ernestine-Bartholomew-Longname');
      startGame();

      cy.get('[data-test="seat-label"]').should('have.length', 4).first().should('contain.text', 'Bartholomew');
      cy.get('[data-test="seat-label"]').then(($labels) => {
        const rects = [...$labels].map((l) => l.getBoundingClientRect());
        rects.forEach((r) => {
          expect(r.left).to.be.at.least(0);
          expect(r.right).to.be.at.most(375);
        });
        // No two seats overlap.
        rects.forEach((a, i) => rects.slice(i + 1).forEach((b) => {
          const overlap = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom;
          expect(overlap, `seats ${i} and later do not overlap`).to.eq(false);
        }));
        // The long name is cut with an ellipsis, not widened.
        const first = $labels[0] as HTMLElement;
        expect(first.scrollWidth, 'name is clipped').to.be.greaterThan(first.clientWidth);
      });
      cy.document().then((doc) => {
        const el = doc.scrollingElement as Element;
        expect(el.scrollWidth, 'no horizontal overflow').to.be.at.most(el.clientWidth + 1);
        expect(el.scrollHeight, 'the page does not scroll').to.be.at.most(el.clientHeight + 1);
      });
    });
  });
});
