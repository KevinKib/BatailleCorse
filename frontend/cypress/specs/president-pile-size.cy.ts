// The cards of the trick pile are big enough to read at a glance (owner feedback: "the central card is small"), on a
// phone and on a wide screen, with one row of opponents (3 to 5 players on a phone) and with two (6 to 8 players).
// The room they take is a reserved one: president-table-fit and president-stillness check that nothing scrolls or moves.
import { hostLobby, joinGuests, noHorizontalScroll } from '../support/presidentRoom';
import { midRoundDeal } from '../support/presidentMidRound';

const NAMES = ['Bob', 'Carol', 'Dan', 'Erin', 'Fay', 'Gus', 'Hal'];

// [width, height, players, the least width of a pile card in px]. Before this change: 40 px on a phone, 54 px at 1280x720.
const CASES: Array<[number, number, number, number]> = [
  [375, 667, 4, 54], [375, 667, 8, 48],
  [360, 640, 4, 54], [360, 640, 8, 48],
  [1280, 720, 4, 66], [1280, 720, 8, 66],
];

CASES.forEach(([width, height, players, least]) => {
  it(`shows the pile cards at least ${least} px wide with ${players} players at ${width}x${height}`, () => {
    cy.viewport(width, height);
    hostLobby('Alice').then((id) => {
      joinGuests(id, NAMES.slice(0, players - 1)).then(() => {
        cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: midRoundDeal(players) });
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
        cy.get('[data-card="HEART_KING"]').click({ force: true });
        cy.get('[data-test="play"]').should('not.be.disabled').click();
        cy.get('[data-test="trick-by"]', { timeout: 15000 }).should('have.text', 'You played');
        cy.get('[data-test="trick-card"] .playing_card').should(($card) => {
          expect($card[0].getBoundingClientRect().width, 'pile card width').to.be.at.least(least);
        });
        // The pile stays clear of every opponent plate, and the felt does not scroll inside itself.
        cy.document().then((doc) => {
          const pile = doc.querySelector('[data-test="trick"]')!.getBoundingClientRect();
          doc.querySelectorAll('[data-test="opponent-seat"]').forEach((seat) => {
            const box = seat.getBoundingClientRect();
            const overlaps = box.left < pile.right && box.right > pile.left && box.top < pile.bottom && box.bottom > pile.top;
            expect(overlaps, `plate ${seat.getAttribute('data-seat')} overlaps the pile`).to.eq(false);
          });
          const felt = doc.querySelector('[data-test="president-table"]')!;
          expect(felt.scrollHeight, 'felt content height').to.be.at.most(felt.clientHeight);
        });
        noHorizontalScroll();
        cy.get('[data-test="seat-bubble"]', { timeout: 10000 }).should('not.exist');
        cy.screenshot(`pile-${players}p-${width}x${height}`, { capture: 'viewport' });
      });
    });
  });
});
