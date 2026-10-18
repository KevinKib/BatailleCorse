// What 3 to 8 players need on a phone (spec section 11, decision 1: the ring of opponents; the arc is the fallback):
// every plate stays inside the screen, nothing scrolls sideways, the page never scrolls, the buttons and the hand keep
// 44 px targets, the opponents never hide the trick, the felt never scrolls inside itself.
import { hostLobby, joinGuests, noHorizontalScroll, scriptedPass, scriptedPlay } from '../support/presidentRoom';
import { midRoundDeal, scriptedLead } from '../support/presidentMidRound';

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

// The same measure in the middle of a round (owner rule: the felt never scrolls inside itself): after a cut (the
// closed-trick badge and the chips), after the passes (the "Passed" markers), and, with 8 players, after the places and
// "Out on a 2" (the markers of a seat that is out). The assertion is absolute: nothing of the felt's content is hidden.
const MID_CASES: Array<[number, number, number[]]> = [
  [375, 667, [3, 4, 6, 8]], [360, 640, [3, 4, 6, 8]], [375, 812, [3, 4, 6, 8]],
];

function feltNeverScrolls(label: string) {
  cy.get('[data-test="president-table"]').should(($felt) => {
    expect($felt[0].scrollHeight, `${label}: felt content height`).to.be.at.most($felt[0].clientHeight);
  });
  cy.document().then((doc) => {
    expect(doc.documentElement.scrollHeight, `${label}: page height`).to.be.at.most(doc.documentElement.clientHeight);
  });
  noHorizontalScroll();
}

MID_CASES.forEach(([width, height, counts]) => {
  counts.forEach((players) => {
    it(`keeps the felt still in the middle of a round with ${players} players at ${width}x${height}`, () => {
      cy.viewport(width, height);
      const shot = (step: string) => cy.get('[data-test="seat-bubble"]', { timeout: 10000 }).should('not.exist').then(() => cy.screenshot(`president-mid-${players}p-${width}x${height}-${step}`, { capture: 'viewport' }));
      hostLobby('Alice').then((id) => {
        joinGuests(id, NAMES.slice(0, players - 1)).then((guests) => {
          cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: midRoundDeal(players) });
          cy.get('[data-test="start"]').should('not.be.disabled').click();
          cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
          const [bob] = guests;

          // Alice leads the king, Bob cuts it with a 2: the closed trick's badge and the rule chips are showing.
          cy.get('[data-card="HEART_KING"]').click({ force: true });
          cy.get('[data-test="play"]').should('not.be.disabled').click();
          scriptedPlay(bob, '2', 1);
          cy.get('[data-test="trick-closed-reason"]').should('have.text', 'Closed by a 2');
          cy.get('[data-test="trick-chips"] li').should('have.length.at.least', 2);
          feltNeverScrolls('after the cut');
          shot('1-cut');

          // Bob leads a four of a kind (it closes the trick), then a single that everybody passes.
          scriptedPlay(bob, '4', 4);
          scriptedPlay(bob, '5', 1);
          guests.slice(1).forEach((guest) => scriptedPass(guest));
          cy.get('[data-test="seat-passed"]').should('have.length', players - 2);
          cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
          feltNeverScrolls('after the passes');
          shot('2-passes');

          // From round 2 on every plate also carries a title line ("Vice-President"). Round 1 has none, so the worst case is
          // measured here by adding that line to every opponent's plate (same classes, same scoped style) and letting Vue's
          // next render drop it again.
          cy.get('[data-test="opponent-seat"] .seat-label').then(($labels) => {
            $labels.each((_, label) => {
              const title = label.ownerDocument.createElement('span');
              title.className = 'seat-title';
              title.setAttribute('data-test', 'seat-title-simulated');
              title.textContent = 'Vice-President';
              [...label.attributes].filter((a) => a.name.startsWith('data-v-')).forEach((a) => title.setAttribute(a.name, a.value));
              label.after(title);
            });
          });
          // The most chips the table can show at once (six): how many cards the trick holds, the trick's binding, its chain, then the three variants that get
          // a chip. The Classic rules show three (the count, what to beat, the rule after a pass); three more are added here.
          cy.get('[data-test="trick-chips"]').then(($list) => {
            const first = $list[0].querySelector('.chip')!;
            ['3 plays of 9 in a row', 'Compulsory play', 'Reduced cut'].forEach((text) => {
              const chip = first.ownerDocument.createElement('li');
              chip.className = 'chip';
              chip.setAttribute('data-test', 'rule-chip-simulated');
              chip.textContent = text;
              [...first.attributes].filter((a) => a.name.startsWith('data-v-')).forEach((a) => chip.setAttribute(a.name, a.value));
              $list[0].appendChild(chip);
            });
          });
          feltNeverScrolls('with a title on every plate and every rule chip');
          cy.get('[data-test="rule-chip-simulated"]').then(($chips) => { $chips.remove(); });
          cy.get('[data-test="seat-title-simulated"]').then(($titles) => { $titles.remove(); });

          if (players === 8) {
            // Alice passes too; Bob leads his last card, a 2: out on a 2, the trick closes.
            cy.get('[data-test="pass"]').should('not.be.disabled').click();
            scriptedPlay(bob, '2', 1);
            cy.get('[data-test="seat-out"]').should('have.length', 1);
            cy.get('[data-test="seat-out"].marker--forbidden').should('exist');
            cy.get('[data-test="trick-closed"]').should('exist');
            feltNeverScrolls('after Out on a 2');
            shot('3-out-on-a-2');

            // The next seat leads and the following ones pass: passes and the out marker together.
            scriptedLead(guests[1]);
            guests.slice(2).forEach((guest) => scriptedPass(guest));
            cy.get('[data-test="seat-passed"]').should('have.length', players - 3);
            cy.get('[data-test="seat-out"]').should('have.length', 1);
            feltNeverScrolls('passes with a seat out');
            shot('4-passes-and-out');
          }
        });
      });
    });
  });
});
