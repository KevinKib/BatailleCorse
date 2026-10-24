// A seat that goes out is seen going out (owner decision, option 2A): once its last card has landed, the bubble
// "Out · 1st" pops over its plate and the medal "1st" takes the fan's reserved slot; the name is not dimmed (a gold
// border instead) and nothing moves. Bob (seat 1) cuts Alice's king with a 2, then empties his hand with sets that close
// the trick or, with eight players, with a last pair: a normal finish, not "Out on a 2".
// `--env before=true` only plays the scenario and takes the screenshots (the "before" captures of the PR, taken on the
// code without the medal).
import { hostLobby, joinGuests, scriptedPlay } from '../support/presidentRoom';
import { expectNothingMoved, resetMoves, stillAcross } from '../support/presidentStillness';

const NAMES = ['Bob', 'Carol', 'Dan', 'Erin', 'Fay', 'Gus', 'Hal'];
const RANKS = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K'];
const DECK = RANKS.flatMap((rank) => ['H', 'D', 'C', 'S'].map((suit) => `${rank}${suit}`));

/** Bob's hand: a 2 to cut with, then fours of a kind (they close the trick, Bob leads again), ending on a set. */
const bobHand = (players: number) => (players === 8
  ? ['2S', '4H', '4D', '4C', '4S', '5H', '5D']
  : ['2S', '4H', '4D', '4C', '4S', '5H', '5D', '5C', '5S', '6H', '6D', '6C', '6S']);

/** Alice (seat 0) holds the king of hearts and leads it; Bob (seat 1) holds `bobHand`, the rest of the deck is filler. */
function finishDeal(players: number): string[] {
  const deal: string[] = new Array(52).fill('');
  deal[0] = 'KH';
  const bob = bobHand(players);
  bob.forEach((code, i) => { deal[1 + i * players] = code; });
  const used = new Set(['KH', ...bob]);
  const rest = DECK.filter((code) => !used.has(code));
  for (let k = 0; k < 52; k++) if (deal[k] === '') deal[k] = rest.shift()!;
  return deal;
}

const CASES: Array<[number, number, number]> = [[375, 667, 4], [360, 640, 8], [1280, 720, 4]];
const before = () => Boolean(Cypress.env('before'));

beforeEach(resetMoves);

/** The finish is told: the bubble is over Bob's plate (before the medal, the marker is the only sign of it). */
const toldOut = () => cy.get(before() ? '[data-seat="1"] [data-test="seat-out"]' : '[data-seat="1"] [data-test="seat-bubble"]', { timeout: 20000 }).should('be.visible');

CASES.forEach(([width, height, players]) => {
  it(`shows a seat going out with ${players} players at ${width}x${height}`, () => {
    cy.viewport(width, height);
    hostLobby('Alice').then((id) => {
      joinGuests(id, NAMES.slice(0, players - 1)).then(([bob]) => {
        cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: finishDeal(players) });
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');

        // Alice leads the king, Bob cuts it with his 2 (he leads), then plays his sets: the last one empties his hand.
        cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
        cy.get('[data-card="HEART_KING"]').click({ force: true });
        cy.get('[data-test="play"]').should('not.be.disabled').click();
        scriptedPlay(bob, '2', 1);
        cy.get('[data-seat="1"] [data-test="seat-out"]').should('not.exist');
        scriptedPlay(bob, '4', 4);
        if (players === 8) {
          cy.get('[data-seat="1"] [data-test="seat-out"]').should('not.exist');
          cy.screenshot(`president-finish-${players}p-${width}x${height}-0-last-set-to-come`, { capture: 'viewport' });
          stillAcross('Bob plays his last set', () => { scriptedPlay(bob, '5', 2); }, () => {
            toldOut();
          });
        } else {
          scriptedPlay(bob, '5', 4);
          cy.get('[data-seat="1"] [data-test="seat-out"]').should('not.exist');
          stillAcross('Bob plays his last set', () => { scriptedPlay(bob, '6', 4); }, () => {
            toldOut();
          });
        }

        // The bubble is told when the last card has landed, with the medal (born together) and the announcement.
        if (!before()) {
          cy.get('[data-seat="1"] [data-test="seat-bubble"]').should(($bubble) => {
            expect($bubble.text(), 'bubble').to.eq('Out · 1st');
            expect($bubble.attr('data-kind')).to.eq('out');
          });
        }
        cy.screenshot(`president-finish-${players}p-${width}x${height}-1-bubble`, { capture: 'viewport' });
        if (!before()) {
          cy.get('[data-seat="1"] [data-test="seat-out"]').should(($medal) => {
            expect($medal.attr('data-place'), 'place').to.eq('1');
            expect($medal.find('[data-test="seat-medal-place"]').text(), 'disc').to.eq('1st');
            expect($medal.find('[data-test="seat-medal-word"]').text(), 'word').to.eq('Out');
            expect($medal.find('.sr-only').text(), 'accessible text').to.eq('Out, 1st place');
          });
          cy.get('[data-test="president-announcements"] li').should('contain.text', 'Bob is out, 1st');
        }

        // The bubble goes, the medal stays; the name is at full opacity with a gold border, unlike a seat still playing.
        if (!before()) cy.get('[data-seat="1"] [data-test="seat-bubble"]', { timeout: 10000 }).should('not.exist');
        cy.screenshot(`president-finish-${players}p-${width}x${height}-2-medal`, { capture: 'viewport' });
        if (!before()) {
          cy.get('[data-seat="1"] [data-test="seat-out"]').should('be.visible');
          cy.get('[data-seat="1"] [data-test="seat-label"]').then(($label) => {
            const style = getComputedStyle($label[0]);
            expect(style.opacity, 'name opacity').to.eq('1');
            cy.get('[data-seat="2"] [data-test="seat-label"]').then(($other) => {
              expect(style.borderTopColor, 'gold border').not.to.eq(getComputedStyle($other[0]).borderTopColor);
            });
          });
          // The medal sits in the fan's slot and never exceeds it: it is whole on screen, and no plate text is clipped.
          cy.get('[data-seat="1"] .fan-picture').then(($fan) => {
            const fan = $fan[0].getBoundingClientRect();
            cy.get('[data-seat="1"] .medal-disc').then(($disc) => {
              const disc = $disc[0].getBoundingClientRect();
              expect(disc.height, 'disc height').to.be.at.most(fan.height + 1);
              expect(disc.top, 'disc top').to.be.at.least(fan.top - 1);
              expect(disc.bottom, 'disc bottom').to.be.at.most(fan.bottom + 1);
              expect(disc.left, 'disc left').to.be.at.least(0);
            });
          });
          cy.get('[data-seat="1"] .medal').then(($medal) => {
            const box = $medal[0].getBoundingClientRect();
            expect(box.right, 'medal right').to.be.at.most(width);
          });
          cy.get('[data-seat="1"] [data-test="seat-label"], [data-seat="1"] [data-test="seat-cards"]').each(($el) => {
            expect($el[0].scrollWidth, 'plate text').to.be.at.most($el[0].clientWidth + 1);
          });
        }
        expectNothingMoved();
      });
    });
  });
});
