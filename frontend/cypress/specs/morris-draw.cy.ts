// Three Men's Morris draws, end to end. The browser is White (the host); a scripted STOMP client is Black. Every wait
// is on a DOM state or on a frame, never on a delay. Points are numbered row by row (0 1 2 / 3 4 5 / 6 7 8).
// The movement phase starts from White 0, 2, 7 and Black 1, 3, 5 (free points 4, 6 and 8), White to move.
import { joinAsScriptedPlayer, waitForFrame, type ScriptedPlayer } from '../support/stompPlayer';

type Scripted = ScriptedPlayer & { token: string; seat: number };

const place = (p: Scripted, point: number) => cy.then(() => p.publish('/app/three-men-morris/place', { point }));
const move = (p: Scripted, from: number, to: number) => cy.then(() => p.publish('/app/three-men-morris/move', { from, to }));
const send = (p: Scripted, command: 'offerDraw' | 'acceptDraw' | 'declineDraw') =>
  cy.then(() => p.publish(`/app/three-men-morris/${command}`, {}));
const owner = (n: number, who: 'white' | 'black' | 'none') => cy.get(`[data-test="point-${n}"]`).should('have.attr', 'data-owner', who);
const myTurn = () => cy.get('[data-test="turn-hint"]').should('contain.text', 'Your turn');
const click = (n: number) => cy.get(`[data-test="point-${n}"]`).click();
const offerButton = () => cy.get('[data-test="draw-offer"]');

/** The browser as White (host, through the UI) against a scripted Black, both pieces down, White to move. */
function inMovementPhase(): Cypress.Chainable<Scripted> {
  cy.visit('/games/three-men-morris');
  cy.get('[data-test="name"]').type('Alice');
  cy.get('[data-test="new-game"]').click();
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  return cy.location('pathname').then((path) => {
    const id = path.split('/room/')[1];
    return joinAsScriptedPlayer(id, 'Bob', 'three-men-morris').then((black) => {
      cy.get('[data-test="start"]').should('not.be.disabled').click();
      cy.get('[data-test="morris-board"]', { timeout: 10000 }).should('be.visible');
      myTurn();
      offerButton().should('not.exist'); // no draw during the placement phase
      click(0); owner(0, 'white'); place(black, 1); owner(1, 'black'); myTurn();
      click(2); owner(2, 'white'); place(black, 3); owner(3, 'black'); myTurn();
      click(7); owner(7, 'white'); place(black, 5); owner(5, 'black'); myTurn();
      cy.get('[data-test="phase-hint"]').should('have.text', 'Move a piece');
      return cy.wrap(black);
    });
  });
}

const drawn = (reason: string) => (f: ScriptedPlayer['frames'][number]) =>
  f.state?.outcome?.status === 'FINISHED' && f.state.outcome.reason === reason && f.state.outcome.winner === null;

describe("Three Men's Morris draws", () => {
  it('offer, refusal, no second offer before my next move, then offer again', () => {
    inMovementPhase().then((black) => {
      offerButton().should('have.text', 'Offer a draw').and('not.be.disabled').click();
      offerButton().should('have.text', 'Draw offered').and('be.disabled');
      waitForFrame(black, (f) => f.eventType === 'DRAW_OFFERED');
      cy.get('[data-test="draw-incoming"]').should('not.exist'); // my own offer is not a banner for me
      send(black, 'declineDraw');
      cy.get('[data-test="board-live"]').should('contain.text', 'Bob declined the draw.');
      offerButton().should('have.text', 'Offer a draw').and('be.disabled'); // once between two of my own moves
      click(7); click(6); owner(6, 'white');
      move(black, 5, 8); owner(8, 'black'); myTurn();
      offerButton().should('not.be.disabled').click();
      offerButton().should('have.text', 'Draw offered');
    });
  });

  it('an offer from the opponent shows an alertdialog; Decline keeps the game going', () => {
    inMovementPhase().then((black) => {
      send(black, 'offerDraw');
      cy.get('[data-test="draw-incoming"]').should('be.visible')
        .and('have.attr', 'role', 'alertdialog').and('contain.text', 'Bob offers a draw');
      cy.get('[data-test="board-live"]').should('contain.text', 'Bob offers a draw.');
      cy.get('[data-test="draw-decline"]').click();
      cy.get('[data-test="draw-incoming"]').should('not.exist');
      waitForFrame(black, (f) => f.eventType === 'DRAW_DECLINED');
      cy.get('[data-test="end"]').should('not.exist');
      myTurn();
    });
  });

  it('a move answers an incoming offer: the banner leaves and the game goes on', () => {
    inMovementPhase().then((black) => {
      send(black, 'offerDraw');
      cy.get('[data-test="draw-incoming"]').should('be.visible');
      click(7); click(6); owner(6, 'white');
      cy.get('[data-test="draw-incoming"]').should('not.exist');
    });
  });

  it('accepting the opponent offer ends the game as a draw on both sides', () => {
    inMovementPhase().then((black) => {
      send(black, 'offerDraw');
      cy.get('[data-test="draw-accept"]').click();
      cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
      cy.get('.end-title').should('have.text', 'DRAW');
      cy.get('[data-cy="victory-flourish"]').should('not.exist');
      cy.get('[data-test="end-winner"]').should('not.exist');
      cy.get('.end-sub').should('have.text', 'Draw agreed.');
      waitForFrame(black, drawn('DRAW_AGREED'));
    });
  });

  it('the opponent accepting my offer ends the game as a draw', () => {
    inMovementPhase().then((black) => {
      offerButton().click();
      waitForFrame(black, (f) => f.eventType === 'DRAW_OFFERED');
      send(black, 'acceptDraw');
      cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
      cy.get('.end-title').should('have.text', 'DRAW');
      cy.get('.end-sub').should('have.text', 'Draw agreed.');
    });
  });

  [[375, 667], [1280, 800]].forEach(([width, height]) => {
    it(`fits at ${width}x${height} with an incoming offer: no scroll, nothing outside, targets of 44 px, no overlap`, () => {
      cy.viewport(width, height);
      inMovementPhase().then((black) => {
        send(black, 'offerDraw');
        cy.get('[data-test="draw-incoming"]').should('be.visible');
        cy.document().then((doc) => {
          const el = doc.scrollingElement as Element;
          expect(el.scrollWidth, 'no horizontal scroll').to.be.at.most(el.clientWidth + 1);
          expect(el.scrollHeight, 'no vertical scroll').to.be.at.most(el.clientHeight + 1);
          const sel: Record<string, string> = {
            board: '[data-test="morris-board"]', tags: '[data-test="player-tag"]', phase: '[data-test="phase-hint"]',
            banner: '[data-test="draw-incoming"]', offer: '[data-test="draw-offer"]', leave: '[data-test="leave"]',
          };
          const boxes = Object.entries(sel).flatMap(([name, s]) => [...doc.querySelectorAll(s)]
            .filter((e) => e.getClientRects().length > 0)
            .map((e, i) => ({ name: `${name}#${i}`, r: e.getBoundingClientRect() })));
          expect(boxes.length).to.be.at.least(7);
          boxes.forEach(({ name, r }) => {
            expect(r.top, `${name} top`).to.be.at.least(-1);
            expect(r.bottom, `${name} bottom`).to.be.at.most(height + 1);
            expect(r.right, `${name} right`).to.be.at.most(width + 1);
          });
          for (let i = 0; i < boxes.length; i++) {
            for (let j = i + 1; j < boxes.length; j++) {
              const a = boxes[i].r;
              const b = boxes[j].r;
              const hit = a.left < b.right - 0.5 && b.left < a.right - 0.5 && a.top < b.bottom - 0.5 && b.top < a.bottom - 0.5;
              expect(hit, `${boxes[i].name} and ${boxes[j].name} must not overlap`).to.eq(false);
            }
          }
          ['draw-accept', 'draw-decline', 'draw-offer'].forEach((name) => {
            const r = doc.querySelector(`[data-test="${name}"]`)!.getBoundingClientRect();
            expect(r.height, `${name} height`).to.be.at.least(44);
          });
          for (let n = 0; n < 9; n++) {
            const r = doc.querySelector(`[data-test="point-${n}"]`)!.getBoundingClientRect();
            expect(r.width, `point ${n} width`).to.be.at.least(44);
          }
        });
      });
    });
  });

  it('Tapatan: the same position three times is a draw, with a warning at the second', () => {
    inMovementPhase().then((black) => {
      cy.get('[data-test="repetition-hint"]').should('not.exist');
      // White 7 to 6, Black 5 to 8, White 6 to 7, Black 8 to 5: the movement phase starts again, twice seen.
      click(7); click(6); owner(6, 'white'); move(black, 5, 8); owner(8, 'black'); myTurn();
      click(6); click(7); owner(7, 'white'); move(black, 8, 5); owner(5, 'black'); myTurn();
      cy.get('[data-test="repetition-hint"]').should('be.visible')
        .and('have.text', 'This position has now appeared twice. A third time is a draw.');
      click(7); click(6); owner(6, 'white'); move(black, 5, 8); owner(8, 'black'); myTurn();
      click(6); click(7); owner(7, 'white');
      move(black, 8, 5); // third occurrence of the first movement position
      cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
      cy.get('.end-title').should('have.text', 'DRAW');
      cy.get('.end-sub').should('have.text', 'Draw by repetition: the same position occurred three times.');
      waitForFrame(black, drawn('DRAW_REPETITION'));
    });
  });
});
