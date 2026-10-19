// President soft rings and exact source of the played cards (owner decisions 2B and 3A of the table canvas), on the
// fixed deal of president-animations (PUT /api/test/president/{id}/deal, test profile only). The browser is seat 0
// (Alice); Bob and Carol are scripted.
//  1. my king leaves the exact place it had in my hand when I clicked Play (not the middle of the hand zone): the
//     dashed ring is on that slot, the landing ring on the card on the pile, both soft (1.5 px, no halo) and at most
//     6 px larger than the card they surround;
//  2. Bob's cut leaves the top back of his fan: the dashed ring is on that back only;
//  3. the closed trick has no closing flash around the pile.
// Every wait is on a DOM state, never a fixed delay.
import { hostLobby, joinGuests, scriptedPlay } from '../support/presidentRoom';

const ALICE = ['KH', 'KD', 'KC', 'KS', '6D', '6C', '2C', '7H', '7D', '7C', '7S', '8H', '8D', '8C', '8S', '9H', '9D', '9C'];
const BOB = ['2S', '3H', '3D', '3C', '3S', '4H', '4D', '4C', '4S', '5H', '5D', '5C', '5S', '6H', '6S', '2H', '2D'];
const CAROL = ['9S', '10H', '10D', '10C', '10S', 'JH', 'JD', 'JC', 'JS', 'QH', 'QD', 'QC', 'QS', 'AH', 'AD', 'AC', 'AS'];

function deal(): string[] {
  const cards: string[] = [];
  for (let i = 0; i < 17; i++) cards.push(ALICE[i], BOB[i], CAROL[i]);
  cards.push(ALICE[17]);
  return cards;
}

interface Box { left: number; top: number; width: number; height: number }
const box = (el: Element): Box => {
  const r = el.getBoundingClientRect();
  return { left: r.left, top: r.top, width: r.width, height: r.height };
};
/** The ring stands off `pad` px around `card` (the soft ring: 2 px), within a rounding of 1.5 px. */
function expectRingAround(ring: Box, card: Box, pad: number, label: string) {
  const tolerance = 1.5;
  expect(ring.left, `${label} left`).to.be.closeTo(card.left - pad, tolerance);
  expect(ring.top, `${label} top`).to.be.closeTo(card.top - pad, tolerance);
  expect(ring.width, `${label} width`).to.be.closeTo(card.width + 2 * pad, tolerance * 2);
  expect(ring.height, `${label} height`).to.be.closeTo(card.height + 2 * pad, tolerance * 2);
  expect(ring.width, `${label} at most the card + 6 px`).to.be.at.most(card.width + 6);
  expect(ring.height, `${label} at most the card + 6 px`).to.be.at.most(card.height + 6);
}

const bob = '.seat-slot[data-seat="1"]';

[{ width: 375, height: 667 }, { width: 1280, height: 720 }].forEach(({ width, height }) => {
  describe(`President soft rings at ${width} px`, () => {
    it('rings only the cards that moved, softly, from their exact place', () => {
      cy.viewport(width, height);
      cy.window().then((win) => win.localStorage.removeItem('animationSpeed'));
      hostLobby('Alice').then((id) => {
        joinGuests(id, ['Bob', 'Carol']).then(([bobPlayer]) => {
          cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: deal() });
          cy.get('[data-test="start"]').should('not.be.disabled').click();
          cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
          cy.get(`${bob} [data-test="fan-back"]`).should('have.length.at.least', 5);
          cy.screenshot(`president-soft-rings-before-play-${width}`, { capture: 'viewport' });

          // 1. My king: measured once the card has settled in its raised position, then played.
          let king: Box | null = null;
          cy.get('[data-card="HEART_KING"]').click({ force: true });
          cy.get('[data-card="HEART_KING"].selected .playing_card').should(($card) => {
            const now = box($card[0]);
            // Raised (the 12 px lift of a selected card is done, not just started) and no longer moving between two reads.
            const lift = new DOMMatrixReadOnly(getComputedStyle($card[0].parentElement!).transform).m42;
            const raised = Math.abs(lift) >= 11;
            const settled = raised && king !== null && Math.abs(king.top - now.top) < 0.5 && Math.abs(king.left - now.left) < 0.5;
            king = now;
            expect(settled, 'the selected king has stopped moving').to.eq(true);
          });
          cy.get('[data-test="play"]').should('not.be.disabled').click();

          // One snapshot of every ring (they last 1.6 s: the checks must not wait on one another).
          cy.get('body').should(($body) => {
            const marks = [...$body[0].querySelectorAll('[data-test="motion-mark"]')] as HTMLElement[];
            const from = marks.filter(m => m.dataset.role === 'from');
            const to = marks.filter(m => m.dataset.role === 'to');
            expect(from.length, 'one dashed ring: the king\'s slot').to.eq(1);
            expect(to.length, 'one landing ring').to.eq(1);
            expectRingAround(box(from[0]), king!, 2, 'source ring of my king');
            expect(getComputedStyle(from[0]).borderStyle).to.eq('dashed');
            expectRingAround(box(to[0]), box($body[0].querySelector('[data-test="trick-cards"] .playing_card')!), 2, 'landing ring of my king');
            marks.forEach((mark) => {
              const style = getComputedStyle(mark);
              // 1.5 px, which the browser rounds to a device pixel (1 px at a scale of 1).
              expect(parseFloat(style.borderTopWidth), 'a thin contour').to.be.within(1, 1.5);
              expect(style.boxShadow, 'no halo').to.eq('none');
            });
          });
          cy.screenshot(`president-soft-rings-my-play-${width}`, { capture: 'viewport' });
          cy.get('[data-test="trick-count"]').should('have.text', '1 card in the trick');

          // 2. Bob's cut leaves the top back of his fan.
          scriptedPlay(bobPlayer, '2', 1);
          cy.get('[data-test="motion-mark"][data-role="from"]').should(($from) => {
            const backs = $from[0].ownerDocument.querySelectorAll(`${bob} .fan-back`);
            const top = box(backs[backs.length - 1]);
            // Mine may still be showing (1.6 s): Bob's is the one over his fan.
            const bobs = [...$from].filter(m => Math.abs(box(m).top + box(m).height / 2 - (top.top + top.height / 2)) < 40);
            expect(bobs.length, 'one dashed ring: the top back').to.eq(1);
            const ring = box(bobs[0]);
            expect(ring.width, 'at most the back + 6 px').to.be.at.most(top.width + 6);
            expect(ring.height, 'at most the back + 6 px').to.be.at.most(top.height + 6);
            expect(ring.left + ring.width / 2, 'centred on the top back').to.be.closeTo(top.left + top.width / 2, 3);
            expect(ring.top + ring.height / 2, 'centred on the top back').to.be.closeTo(top.top + top.height / 2, 3);
          });
          cy.get('[data-test="trick-closed-reason"]').should('have.text', 'Closed by a 2');
          // 3. No closing flash around the closed play.
          cy.get('.trick-plate--closed').should(($plate) => {
            expect(getComputedStyle($plate[0], '::before').content, 'no flash pseudo-element').to.be.oneOf(['none', 'normal']);
          });
          cy.screenshot(`president-soft-rings-cut-${width}`, { capture: 'viewport' });
        });
      });
    });
  });
});
