// President table pictures (spec §11 decision 7, option B of the animations canvas) on a fixed deal (PUT
// /api/test/president/{id}/deal, test profile only). The browser is seat 0 (Alice); Bob and Carol are scripted.
//  1. Alice's king flies to the pile: a copy of the card travels, the rings mark its way;
//  2. Bob cuts it with a 2: the copy flies from his fan, the pile keeps his 2 with "Closed by a 2 / Bob leads", and his
//     fan drops to 16 cards once it landed;
//  3. Bob's four 3s close the trick: "Four of a kind";
//  4. Carol passes on Bob's pair of 6s: a "Pass" bubble pops over her seat, which is dimmed;
//  5. at the Instant speed nothing flies: Bob's last two cards, 2s, only get still rings, and the "Out on a 2" bubble.
// Every wait is on a DOM state, never a fixed delay.
import { hostLobby, joinGuests, noHorizontalScroll, scriptedPass, scriptedPlay } from '../support/presidentRoom';

const ALICE = ['KH', 'KD', 'KC', 'KS', '6D', '6C', '2C', '7H', '7D', '7C', '7S', '8H', '8D', '8C', '8S', '9H', '9D', '9C'];
const BOB = ['2S', '3H', '3D', '3C', '3S', '4H', '4D', '4C', '4S', '5H', '5D', '5C', '5S', '6H', '6S', '2H', '2D'];
const CAROL = ['9S', '10H', '10D', '10C', '10S', 'JH', 'JD', 'JC', 'JS', 'QH', 'QD', 'QC', 'QS', 'AH', 'AD', 'AC', 'AS'];

function deal(): string[] {
  const cards: string[] = [];
  for (let i = 0; i < 17; i++) cards.push(ALICE[i], BOB[i], CAROL[i]);
  cards.push(ALICE[17]);
  return cards;
}

const bob = '.seat-slot[data-seat="1"]';
const carol = '.seat-slot[data-seat="2"]';

/** The felt holds the seats, the closed pile and its chips without scrolling inside itself, every chip on it. */
function feltFits() {
  cy.get('[data-test="president-table"]').should(($felt) => {
    const felt = $felt[0];
    expect(felt.scrollHeight, 'felt content height').to.be.at.most(felt.clientHeight + 1);
    const box = felt.getBoundingClientRect();
    felt.querySelectorAll('[data-test="trick-chips"] li, [data-test="trick-closed"]').forEach((el) => {
      const r = el.getBoundingClientRect();
      expect(r.bottom, `${el.textContent} inside the felt`).to.be.at.most(box.bottom);
      expect(r.top, `${el.textContent} inside the felt`).to.be.at.least(box.top);
    });
  });
}

/** No ring covers the count written under a fan, nor the badge of a closed trick. */
function countsReadable() {
  // Measured at once (no retry): a retry would pass as soon as the rings fade out.
  cy.get('body').then(($body) => {
    const marks = [...$body[0].querySelectorAll('[data-test="motion-mark"]')].map((m) => m.getBoundingClientRect());
    $body[0].querySelectorAll('[data-test="opponent-seat"] [data-test="seat-cards"], [data-test="trick-closed"]').forEach((el) => {
      const t = el.getBoundingClientRect();
      marks.forEach((m) => {
        const overlaps = m.left < t.right && m.right > t.left && m.top < t.bottom && m.bottom > t.top;
        expect(overlaps, `a ring covers "${el.textContent}"`).to.eq(false);
      });
    });
  });
}

[375, 1280].forEach((width) => {
  describe(`President table pictures at ${width} px`, () => {
    it('shows plays flying, passes as bubbles, closed tricks with their reason and the hands as fans', () => {
      // 1280x720: the size of the runner's window, so that a screenshot does not resize the page under the rings.
      cy.viewport(width, width === 375 ? 667 : 720);
      // Normal speed, whatever an earlier spec left in the browser.
      cy.window().then((win) => win.localStorage.removeItem('animationSpeed'));
      hostLobby('Alice').then((id) => {
        joinGuests(id, ['Bob', 'Carol']).then(([bobPlayer, carolPlayer]) => {
          cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: deal() });
          cy.get('[data-test="start"]').should('not.be.disabled').click();
          cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');

          // Every opponent shows its hand as a fan of backs, with the number written under it.
          cy.get(`${bob} [data-test="seat-fan"]`).should('be.visible');
          cy.get(`${bob} [data-test="fan-back"]`).should('have.length.at.least', 5);
          cy.get(`${bob} [data-test="seat-cards"]`).should('have.text', '17 cards');
          cy.get(`${carol} [data-test="seat-cards"]`).should('have.text', '17 cards');
          cy.get('[data-test="speed-button"]').should('have.attr', 'aria-label', 'Animation speed: Normal');

          // 1. My king flies to the pile.
          cy.get('[data-card="HEART_KING"]').click({ force: true });
          cy.get('[data-test="play"]').should('not.be.disabled').click();
          cy.get('[data-test="motion-card"]').should('exist');
          cy.get('[data-test="motion-mark"]').should('exist');
          cy.get('[data-test="trick-count"]').should('have.text', '1 card in the trick');

          // 2. Bob's cut: the card flies from his fan (an arrow for an opponent), the pile says why and who leads.
          scriptedPlay(bobPlayer, '2', 1);
          cy.get('[data-test="motion-arrow"]').should('exist');
          cy.get('[data-test="motion-mark"]').should('exist');
          countsReadable();
          cy.get('[data-test="trick-closed-reason"]').should('have.text', 'Closed by a 2');
          cy.get('[data-test="trick-closed-leader"]').should('have.text', 'Bob leads');
          countsReadable();
          cy.get('[data-test="closed-trick-card"]').should('have.length', 1);
          cy.get('[data-test="trick-count"]').should('have.text', '2 cards in the trick');
          // The count drops when the card has landed (the state already says 16).
          cy.get(`${bob} [data-test="seat-cards"]`).should('have.text', '16 cards');
          cy.get('[data-test="motion-card"]').should('not.exist');
          feltFits();
          cy.screenshot(`president-animations-cut-${width}`, { capture: 'viewport' });
          noHorizontalScroll();

          // The closed trick's cards sweep to Bob (copies fly from the pile), then the pile holds no card, only the badge.
          cy.get('[data-test="trick-cards"].trick-cards--swept', { timeout: 10000 }).should('exist');
          cy.get('[data-test="trick-closed-reason"]').should('have.text', 'Closed by a 2');
          cy.get('[data-test="trick-count"]').should('have.text', '2 cards in the trick');
          cy.get('[data-test="motion-card"]', { timeout: 10000 }).should('not.exist');
          cy.get('[data-test="trick-closed"]').should('be.visible');
          cy.screenshot(`president-animations-swept-${width}`, { capture: 'viewport' });
          feltFits();

          // 3. A four of a kind closes the trick too.
          scriptedPlay(bobPlayer, '3', 4);
          cy.get('[data-test="trick-closed-reason"]').should('have.text', 'Four of a kind');
          scriptedPlay(bobPlayer, '4', 4);
          scriptedPlay(bobPlayer, '5', 4);

          // 4. Carol passes on the pair of 6s: a bubble over her seat, which sits back.
          scriptedPlay(bobPlayer, '6', 2);
          cy.get('[data-test="trick-closed"]', { timeout: 20000 }).should('not.exist');
          scriptedPass(carolPlayer);
          cy.get(`${carol} [data-test="seat-bubble"]`).should('have.text', 'Pass').and('be.visible');
          cy.get(`${carol} .president-seat`).should('have.class', 'president-seat--passed');
          feltFits();
          cy.screenshot(`president-animations-pass-${width}`, { capture: 'viewport' });
          // The bubble goes after a moment; the "Passed" marker stays while the trick lasts.
          cy.get(`${carol} [data-test="seat-bubble"]`).should('not.exist');
          cy.get(`${carol} [data-test="seat-passed"]`).should('have.text', 'Passed');

          // 5. Instant: Normal, Fast, Instant. Nothing flies any more; the rings stay still; the bubbles still say it.
          cy.get('[data-test="speed-button"]').click();
          cy.get('[data-test="speed-button"]').click();
          cy.get('[data-test="speed-button"]').should('have.attr', 'aria-label', 'Animation speed: Instant');
          cy.get('[data-test="motion-card"]').should('not.exist');
          cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
          cy.get('[data-test="pass"]').should('not.be.disabled').click();
          cy.get('[data-test="my-zone"] [data-test="seat-bubble"]').should('have.text', 'Pass');
          scriptedPlay(bobPlayer, '2', 2);
          cy.get(`${bob} [data-test="seat-bubble"]`).should('have.attr', 'data-kind', 'outForbidden').and('have.text', 'Out on a 2');
          cy.get('[data-test="motion-mark"][data-still="true"]').should('exist');
          cy.get('[data-test="motion-card"]').should('not.exist');
          cy.get(`${bob} [data-test="seat-cards"]`).should('have.text', '0 cards');
          cy.get(`${bob} [data-test="fan-back"]`).should('not.exist');
          noHorizontalScroll();
        });
      });
    });
  });
});
