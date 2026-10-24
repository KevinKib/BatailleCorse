// The board "The exchange" and the pair by pair exchange of President, end to end (spec 2026-10-24, sections 5 and 11).
//  1. Five scripted players, the browser Neutral (Dan 1st, Erin 2nd, the browser 3rd, Bob 4th, Carol last): round 2 pairs
//     Dan with Carol and Erin with Bob, the browser is in no pair. The board sits in the pile's slot over the trick plate
//     and says so ("You are Neutral"), with both rows; the pairs are exchanged one after the other (the second row is
//     still "giving" while the first has landed), a row says done only once its cards have landed, the reading bar is
//     drawn from the last landing, the Seen button acknowledges at once, and the board leaves with the first trick.
//  2. Eight players, six bots, the browser's twin and a held-back guest: TWO (2 pairs) at 360x640 and SCALED (4 pairs)
//     at 375x667: the board fits the pile's slot, nothing overflows, the felt keeps its height.
// Every wait is on a DOM state, never a fixed delay. The screenshots are copied to docs/screenshots/ by hand.
import {
  awaitState, hostLobby, joinGuests, noHorizontalScroll, scriptedPass, scriptedPlay, scriptedReady, scriptedSeeExchange,
  type Guest,
} from '../support/presidentRoom';
import { autoplay, connectMyself } from '../support/presidentAutoplay';

const speedButton = '[data-test="speed-button"]';
const rowStates = ($rows: JQuery<HTMLElement>) => [...$rows].map((row) => row.getAttribute('data-state'));

/**
 * The deal of round 1 (seat k holds the cards at k, k + 5, ...; seats 0 and 1 get 11 cards, the others 10): Dan (seat 3)
 * holds the king of hearts and goes out first on three triples and a single; Erin (seat 4) second; the browser (seat 0)
 * third on three triples and a pair; Bob (seat 1) fourth; Carol (seat 2) is last. The lead goes to the next seat each time.
 */
function fiveHands(): string[][] {
  const browser = ['6H', '6D', '6C', '5H', '5D', '5C', '4H', '4D', '4C', '3H', '3D'];
  const bob = ['AH', 'AD', 'AC', 'AS', 'KS', 'QS', 'JS', '10C', '10S', '9S', '8S'];
  const carol = ['7S', '6S', '5S', '4S', '3C', '3S', '2H', '2D', '2C', '2S'];
  const dan = ['KH', 'KD', 'KC', 'QH', 'QD', 'QC', 'JH', 'JD', 'JC', '10H'];
  const erin = ['9H', '9D', '9C', '8H', '8D', '8C', '7H', '7D', '7C', '10D'];
  return [browser, bob, carol, dan, erin];
}

function dealRoundRobin(hands: string[][]): string[] {
  const deal: string[] = [];
  for (let i = 0; i < 11; i++) hands.forEach((hand) => { if (i < hand.length) deal.push(hand[i]); });
  return deal;
}

const NAMES = ['Bob', 'Carol', 'Dan', 'Erin'];
const SUITS: Record<string, string> = { H: 'HEART', D: 'DIAMOND', C: 'CLUB', S: 'SPADE' };

const browserPass = () => {
  cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
  cy.get('[data-test="pass"]').should('not.be.disabled').click();
};

/** The browser leads a set of the cards it holds (by code, "6H"), and the others pass in turn. */
const browserPlays = (codes: string[]) => {
  cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
  codes.forEach((code) => cy.get(`[data-card="${SUITS[code.slice(-1)]}_${code.slice(0, -1)}"]`).click({ force: true }));
  cy.get('[data-test="play"]').should('not.be.disabled').click();
};

/** Round 1 played so that the browser finishes third (Neutral of five), then every seat presses ready. */
function playRoundOne(bob: Guest, carol: Guest, dan: Guest, erin: Guest) {
  // Dan (seat 3) leads; after each of his sets Erin, the browser, Bob and Carol pass.
  ['KING', 'QUEEN', 'JACK'].forEach((rank) => {
    scriptedPlay(dan, rank, 3);
    scriptedPass(erin); browserPass(); scriptedPass(bob); scriptedPass(carol);
  });
  // Out, 1st: the player who takes 1st place closes the trick, so the lead goes to Erin at once.
  scriptedPlay(dan, '10', 1);
  ['9', '8', '7'].forEach((rank) => {
    scriptedPlay(erin, rank, 3);
    browserPass(); scriptedPass(bob); scriptedPass(carol);
  });
  // Out, 2nd: the last play stays on the pile until the others have passed, then the lead goes to the browser.
  scriptedPlay(erin, '10', 1);
  browserPass(); scriptedPass(bob); scriptedPass(carol);
  [['6H', '6D', '6C'], ['5H', '5D', '5C'], ['4H', '4D', '4C']].forEach((set) => {
    browserPlays(set);
    scriptedPass(bob); scriptedPass(carol);
  });
  // Out, 3rd: the pair stays on the pile until Bob and Carol have passed, then the lead goes to Bob.
  browserPlays(['3H', '3D']);
  scriptedPass(bob); scriptedPass(carol);
  // The four aces close the trick at once; the others are lone cards or a pair that Carol lets go.
  scriptedPlay(bob, 'ACE', 4);
  [['KING', 1], ['QUEEN', 1], ['JACK', 1], ['10', 2], ['9', 1]].forEach(([rank, count]) => {
    scriptedPlay(bob, rank as string, count as number);
    scriptedPass(carol);
  });
  // Back to Normal for the exchange of round 2 (one click from Instant), before the round is over.
  cy.get(speedButton).click();
  cy.get(speedButton).should('have.attr', 'aria-label', 'Animation speed: Normal');
  scriptedPlay(bob, '8', 1); // out, 4th: Carol is last, the round is over
  cy.get('[data-test="round-over-title"]', { timeout: 20000 }).should('have.text', 'Round 1 is over');
  cy.get('[data-test="motion-card"]', { timeout: 10000 }).should('not.exist');
  cy.get('[data-test="ready"]').click();
  [bob, carol, dan, erin].forEach(scriptedReady);
}

[[375, 667], [375, 812], [1280, 720]].forEach(([width, height]) => {
  describe(`President exchange board, five players, Neutral, at ${width}x${height}`, () => {
    it('sequences the pairs, keeps a row pending until its cards land, and leaves with the first trick', () => {
      cy.viewport(width, height);
      // Round 1 at Instant (nothing to look at), round 2 at Normal.
      cy.window().then((win) => win.localStorage.setItem('animationSpeed', 'instant'));
      hostLobby('Alice').then((id) => joinGuests(id, NAMES, { holdExchange: true }).then(([bob, carol, dan, erin]) => {
        cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: dealRoundRobin(fiveHands()) });
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
        cy.get(speedButton).should('have.attr', 'aria-label', 'Animation speed: Instant');
        // A probe on the board's rows: what each row said, in order (a poll could miss the second row's short "giving").
        cy.window().then((win) => {
          const history: string[] = [];
          (win as any).__rowHistory = history;
          new win.MutationObserver(() => {
            const rows = [...win.document.querySelectorAll('[data-test="board-row"]')];
            if (rows.length !== 2) return;
            const now = rows.map((row) => row.getAttribute('data-state')).join('+');
            if (history[history.length - 1] !== now) history.push(now);
          }).observe(win.document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['data-state'] });
        });
        playRoundOne(bob, carol, dan, erin);
        cy.get('[data-test="round"]', { timeout: 30000 }).should('have.text', 'Round 2 of 5');

        // The board is in the pile's slot over the trick plate, with both rows and the Neutral note.
        cy.get('.table-center [data-test="exchange-board"]').should('be.visible');
        cy.get('[data-test="board-row"]').should('have.length', 2);
        cy.get('[data-test="board-neutral"]').should('have.text', 'You are Neutral: no exchange for you');
        cy.get('[data-test="board-seen"]').should('have.class', 'seen--off').and('not.be.visible');

        // The pairs one after the other: a probe on the rows saw the second row still giving its best cards while the
        // first had landed, and never the second landing before the first.
        cy.screenshot(`exchange-sequenced-1-pair-two-giving-${width}x${height}`, { capture: 'viewport' });
        cy.get('[data-test="board-row"]').should(($rows) => {
          expect(rowStates($rows), 'both landed').to.deep.eq(['choosing', 'choosing']);
        });
        cy.window().then((win) => {
          const history = (win as any).__rowHistory as string[];
          expect(history, 'row states over time').to.include('giving+giving');
          expect(history, 'row 1 landed, row 2 giving').to.include('choosing+giving');
          expect(history, 'the second row never lands first').not.to.include('giving+choosing');
          expect(history.indexOf('choosing+giving'), 'in order').to.be.greaterThan(history.indexOf('giving+giving'));
        });
        cy.get('[data-test="board-back"]').should('have.length', 2).each(($back) => {
          expect($back.text(), 'the high seat is choosing').to.match(/is choosing \d/);
        });
        cy.get('[data-test="president-announcements"] li').should('have.length.at.least', 2);

        // Dan and Erin (the high seats) give back: the rows return, then are done once the cards have landed.
        awaitState(dan, 'Dan owes two cards', (s) => s.phase === 'EXCHANGE' && s.toGiveBack === 2);
        cy.then(() => dan.publish('/app/president/giveBack', { cards: dan.lastState().hand.slice(0, 2) }));
        awaitState(erin, 'Erin owes a card', (s) => s.phase === 'EXCHANGE' && s.toGiveBack === 1);
        cy.then(() => erin.publish('/app/president/giveBack', { cards: erin.lastState().hand.slice(0, 1) }));
        cy.get('[data-test="board-row"]').should(($rows) => {
          expect(rowStates($rows), 'both done').to.deep.eq(['done', 'done']);
        });
        cy.get('[data-test="motion-card"]', { timeout: 10000 }).should('not.exist');

        // The reading bar is drawn and the Seen button waits for me (the table is held back by the guests).
        cy.get('[data-test="board-bar-fill"]').should('exist');
        cy.get('[data-test="board-seen"]').should('not.have.class', 'seen--off').and('be.visible');
        cy.get('[data-test="president-table"]').should(($felt) => {
          expect($felt[0].scrollHeight, 'felt content height').to.be.at.most($felt[0].clientHeight + 1);
        });
        cy.get('[data-test="board-rows"]').should(($rows) => {
          expect($rows[0].scrollHeight, 'rows scroll inside the board').to.be.at.most($rows[0].clientHeight + 1);
        });
        noHorizontalScroll();
        cy.screenshot(`exchange-sequenced-2-done-${width}x${height}`, { capture: 'viewport' });

        // Seen acknowledges at once; the guests see it too, the first trick starts and the board leaves with it.
        cy.get('[data-test="board-seen"]').click();
        cy.get('[data-test="board-seen"]').should('have.class', 'seen--off');
        [bob, carol, dan, erin].forEach(scriptedSeeExchange);
        cy.get('[data-test="exchange-board"]', { timeout: 10000 }).should('not.exist');
        cy.get('[data-test="trick"]').should('be.visible');
      }));
    });
  });
});

// Eight players: six bots (they answer at once under the test profile), a scripted guest that plays by itself but holds
// the exchange view back, and a scripted twin of the browser's seat that plays round 1. Whatever title the browser ends
// with, round 2 opens on an exchange of `rows` pairs.
[['two', 360, 640, 2], ['scaled', 375, 667, 4]].forEach(([mode, width, height, rows]) => {
  describe(`President exchange board with 8 players, ${mode}, at ${width}x${height}`, () => {
    it('fits the pile slot with a row per pair and moves nothing around it', () => {
      cy.viewport(width as number, height as number);
      cy.window().then((win) => win.localStorage.removeItem('animationSpeed'));
      hostLobby('Alice').then((id) => joinGuests(id, ['Bob']).then(([bob]) => {
        for (let i = 0; i < 6; i++) cy.get('[data-test="add-bot"]').click();
        cy.get('[data-test="player-count"]').should('contain.text', '8 / 8');
        cy.get('[data-test="customise"]').click();
        cy.get(`[data-test="option-exchange-${mode}"]`).check({ force: true });
        cy.get(`[data-test="option-exchange-${mode}"]`).should('be.checked');
        autoplay(id, bob, { seeExchange: false });
        cy.wrap(null, { log: false }).as('twin');
        connectMyself(id).then((me) => { autoplay(id, me, { ready: false }); cy.wrap(me, { log: false }).as('twin'); });
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
        cy.get('[data-test="round-over-title"]', { timeout: 120000 }).should('have.text', 'Round 1 is over');
        cy.get('[data-test="motion-card"]', { timeout: 20000 }).should('not.exist');
        cy.get('[data-test="ready"]').click();
        cy.get('[data-test="round"]', { timeout: 30000 }).should('have.text', 'Round 2 of 5');
        cy.get('[data-test="exchange-board"]').should('be.visible');
        cy.screenshot(`exchange-sequenced-3-8-players-${mode}-flying-${width}x${height}`, { capture: 'viewport' });
        // The browser has read the exchange and waits for Bob: a row per pair, all done, inside the pile's slot.
        cy.get('[data-test="phase-text"]', { timeout: 40000 }).should('have.text', 'Waiting for Bob to see the exchange');
        cy.get('[data-test="exchange-board"]').should(($board) => {
          const doc = $board[0].ownerDocument;
          const felt = doc.querySelector('[data-test="president-table"]') as HTMLElement;
          const list = doc.querySelector('[data-test="board-rows"]') as HTMLElement;
          const box = $board[0].getBoundingClientRect();
          const feltBox = felt.getBoundingClientRect();
          const rowEls = [...doc.querySelectorAll('[data-test="board-row"]')];
          expect(rowEls.length, 'a row per pair').to.eq(rows);
          expect(rowEls.map((r) => r.getAttribute('data-state')), 'all done').to.deep.eq(new Array(rows).fill('done'));
          expect(box.left, 'board left').to.be.at.least(feltBox.left);
          expect(box.right, 'board right').to.be.at.most(feltBox.right);
          expect(box.top, 'board top').to.be.at.least(feltBox.top);
          expect(box.bottom, 'board bottom').to.be.at.most(feltBox.bottom);
          // SCALED with 8 players at 360x640 may scroll inside the board; at 375x667 and wider it must not.
          expect(list.scrollHeight, 'rows scroll inside the board').to.be.at.most(list.clientHeight + 1);
          expect(doc.documentElement.scrollWidth, 'page width').to.be.at.most(doc.documentElement.clientWidth);
          expect(doc.documentElement.scrollHeight, 'page height').to.be.at.most(doc.defaultView!.innerHeight);
        });
        cy.screenshot(`exchange-sequenced-4-8-players-${mode}-done-${width}x${height}`, { capture: 'viewport' });
        // The board takes no height from the felt: the overflow is the same once it has left with the first trick.
        const overflow = ($felt: JQuery<HTMLElement>) => $felt[0].scrollHeight - $felt[0].clientHeight;
        cy.get('[data-test="president-table"]').then(($felt) => {
          const withBoard = overflow($felt);
          cy.then(() => bob.seeExchange());
          cy.get('[data-test="exchange-board"]', { timeout: 20000 }).should('not.exist');
          cy.get('@twin').then((me) => {
            awaitState(me as any, 'round 2 is on', (st) => st.round === 2 && st.phase === 'PLAYING');
            cy.then(() => (me as any).disconnect());
          });
          cy.get('[data-test="president-table"]').should(($after) => {
            expect(withBoard, 'felt overflow with the board shown').to.be.at.most(overflow($after) + 1);
          });
        });
      }));
    });
  });
});
