// Non-regression: in suit claim mode the hand is grouped by suit (claim cycle order), then by
// rank in the suit, on at most two rows and without scrolling.
const SUIT_ORDER = ['hearts', 'diamonds', 'clubs', 'spades'];
const RANK_ORDER = ['Ace', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'Jack', 'Queen', 'King'];

function startSuitGame() {
  cy.visit('/');
  cy.get('[data-test="play-bullshit"]').click();
  cy.get('[data-test="name"]').type('Alice');
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/bullshit\/room\/.+/);
  // The host picks the options in the lobby; the server's answer marks the choice.
  cy.contains('[data-test="lobby"] label', 'By suit').click();
  cy.get('[data-test="option-claimMode"] .choice--on').should('contain.text', 'By suit');
  cy.contains('[data-test="lobby"] label', '52 cards').click();
  cy.get('[data-test="option-deckSize"] .choice--on').should('contain.text', '52 cards');
  cy.url().then((url) => cy.request('POST', `/api/bullshit/game/${url.split('/room/')[1]}/join`, { name: 'Bob' }));
  cy.get('[data-test="start"]', { timeout: 10000 }).should('not.be.disabled').click();
  cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
}

describe('Bullshit hand sorted by suit in suit claim mode', () => {
  [[375, 667], [1280, 720]].forEach(([vw, vh]) => {
    it(`groups by suit then rank, on two rows with no scroll at ${vw}x${vh}`, () => {
      cy.viewport(vw, vh);
      startSuitGame();
      cy.get('[data-test^="hand-card-"]').should('have.length', 26).then(($cards) => {
        const cards = [...$cards].map((el) => (el.getAttribute('aria-label') as string).split(' of '));
        const keys = cards.map(([rank, suit]) => [SUIT_ORDER.indexOf(suit), RANK_ORDER.indexOf(rank)]);
        keys.forEach(([s, r]) => { expect(s).to.be.at.least(0); expect(r).to.be.at.least(0); });
        for (let i = 1; i < keys.length; i++) {
          const [ps, pr] = keys[i - 1];
          const [s, r] = keys[i];
          expect(s > ps || (s === ps && r > pr), `card ${i} follows card ${i - 1}`).to.eq(true);
        }
        const tops = new Set([...$cards].map((el) => Math.round(el.getBoundingClientRect().top)));
        expect(tops.size, 'at most two rows').to.be.at.most(2);
      });
      cy.document().then((doc) => {
        const el = doc.scrollingElement as Element;
        expect(el.scrollHeight, 'no vertical scroll').to.be.at.most(el.clientHeight + 1);
        expect(el.scrollWidth, 'no horizontal scroll').to.be.at.most(el.clientWidth + 1);
      });
    });
  });
});
