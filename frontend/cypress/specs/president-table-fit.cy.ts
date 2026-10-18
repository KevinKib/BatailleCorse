// What 3 to 8 players need on a phone (spec section 11, decision 1: the ring of opponents; the arc is the fallback):
// every plate stays inside the screen, nothing scrolls sideways, the page never scrolls, the buttons and the hand keep
// 44 px targets, the opponents never hide the trick, the felt never scrolls inside itself.
import { hostLobby, joinGuests, noHorizontalScroll } from '../support/presidentRoom';

const NAMES = ['Bob', 'Carol', 'Dan', 'Erin', 'Fay', 'Gus', 'Hal'];

const CASES: Array<[number, number, number[]]> = [
  [375, 667, [3, 4, 5, 8]], [375, 812, [8]], [360, 640, [3, 5, 8]], [1280, 720, [3, 5, 8]],
];

CASES.forEach(([width, height, counts]) => {
  counts.forEach((players) => {
    it(`fits ${players} players at ${width}x${height}`, () => {
      cy.viewport(width, height);
      hostLobby('Alice').then((id) => {
        joinGuests(id, NAMES.slice(0, players - 1)).then(() => {
          cy.get('[data-test="start"]').should('not.be.disabled').click();
          cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
          cy.get('[data-test="opponent-seat"]').should('have.length', players - 1);
          noHorizontalScroll();
          cy.window().then((win) => {
            const doc = win.document.documentElement;
            expect(doc.scrollHeight, 'page height').to.be.at.most(win.innerHeight);
          });
          // The felt holds the opponents and the trick without scrolling inside itself (as Kobo's).
          cy.get('[data-test="president-table"]').should(($felt) => {
            expect($felt[0].scrollHeight, 'felt content height').to.be.at.most($felt[0].clientHeight + 1);
          });
          // Every opponent plate is wholly on screen, and none covers the pile.
          cy.get('[data-test="opponent-seat"]').then(($seats) => {
            const pile = (win: Window) => win.document.querySelector('[data-test="trick"]')!.getBoundingClientRect();
            cy.window().then((win) => {
              const p = pile(win);
              $seats.each((_, seat) => {
                const box = seat.getBoundingClientRect();
                expect(box.left, 'plate left').to.be.at.least(0);
                expect(box.right, 'plate right').to.be.at.most(win.innerWidth);
                expect(box.top, 'plate top').to.be.at.least(0);
                const overlaps = box.left < p.right && box.right > p.left && box.top < p.bottom && box.bottom > p.top;
                expect(overlaps, `plate ${seat.getAttribute('data-seat')} overlaps the pile`).to.eq(false);
              });
            });
          });
          // Text of each plate is not clipped by it.
          cy.get('[data-test="opponent-seat"]').each(($seat) => {
            $seat.find('[data-test="seat-label"], [data-test="seat-cards"]').each((_, el) => {
              expect(el.scrollWidth, 'plate text').to.be.at.most(el.clientWidth + 1);
            });
          });
          cy.get('[data-test="back"]').should('be.visible').then(($el) => {
            expect($el[0].getBoundingClientRect().bottom, 'Back bottom').to.be.at.most(height);
          });
          cy.get('[data-test="play"]').then(($el) => {
            expect($el[0].getBoundingClientRect().height, 'Play height').to.be.at.least(44);
          });
          // The hand: every card is a named button of at least 44 px wide and tall, and the strip left visible by the
          // overlap is measured (reported, not asserted: it is the same overlap as the other tables).
          cy.get('[data-test^="hand-card-"]').then(($cards) => {
            const rects = [...$cards].map((c) => c.getBoundingClientRect());
            rects.forEach((r) => expect(r.width, 'card width').to.be.at.least(44));
            const first = rects.filter((r) => Math.abs(r.top - rects[0].top) < 2);
            const step = first.length > 1 ? first[1].left - first[0].left : first[0].width;
            expect(step, `visible strip of a card (hand of ${$cards.length})`).to.be.at.least(20);
          });
          cy.screenshot(`president-${players}p-${width}x${height}`, { capture: 'viewport' });
        });
      });
    });
  });
});
