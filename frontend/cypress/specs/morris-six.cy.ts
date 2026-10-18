// Six Men's Morris, played end to end. The browser is White (the host, through the UI); a scripted STOMP client is Black.
// Every wait is on a DOM state or on a frame, never on a delay. Points: the outer square is 0..7 clockwise from the top
// left (corners 0 2 4 6, middles 1 3 5 7), the inner square 8..15 in the same order (i + 8 is the inner twin of i).
// A line of three is a mill (the sides of a square); a mill takes an opponent piece outside any mill; a player left with
// two pieces loses. Under the `test` profile a "random" host is White, and a bot picks its first legal decision.
import { joinAsScriptedPlayer, waitForFrame, type ScriptedPlayer } from '../support/stompPlayer';

type Scripted = ScriptedPlayer & { token: string; seat: number };

const place = (p: Scripted, point: number) => cy.then(() => p.publish('/app/three-men-morris/place', { point }));
const move = (p: Scripted, from: number, to: number) => cy.then(() => p.publish('/app/three-men-morris/move', { from, to }));
const take = (p: Scripted, point: number) => cy.then(() => p.publish('/app/three-men-morris/remove', { point }));
const send = (p: Scripted, command: 'offerDraw' | 'acceptDraw' | 'declineDraw') =>
  cy.then(() => p.publish(`/app/three-men-morris/${command}`, {}));
const owner = (n: number, who: 'white' | 'black' | 'none') => cy.get(`[data-test="point-${n}"]`).should('have.attr', 'data-owner', who);
const myTurn = () => cy.get('[data-test="turn-hint"]').should('contain.text', 'Your turn');
const click = (n: number) => cy.get(`[data-test="point-${n}"]`).click();
const lost = (colour: 'white' | 'black') => cy.get(`[data-test="player-tag"][data-colour="${colour}"] [data-test="tag-lost"]`);
const removable = (n: number) => cy.get(`[data-test="point-${n}"]`).should('have.attr', 'data-removable', 'true');
const protectedPiece = (n: number) => cy.get(`[data-test="point-${n}"]`).should('have.attr', 'data-protected', 'true');
const SIZES: Array<[number, number]> = [[375, 667], [1280, 800]];

/** The browser as White (host, through the UI) against a scripted Black, Six Men's Morris, the game started. */
function asWhiteInSix(): Cypress.Chainable<{ id: string; black: Scripted }> {
  cy.visit('/games/three-men-morris');
  cy.get('[data-test="name"]').type('Alice');
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/three-men-morris\/room\/.+/);
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  cy.get('[data-test="variant-card-six"]').click();
  cy.get('[data-test="option-variant-six"]').should('be.checked');
  cy.get('[data-test="colour-white"]').click();
  cy.get('[data-test="option-hostColour-white"]').should('be.checked');
  return cy.location('pathname').then((path) => {
    const id = path.split('/room/')[1];
    return joinAsScriptedPlayer(id, 'Bob', 'three-men-morris').then((black) => {
      cy.get('[data-test="start"]').should('not.be.disabled').click();
      cy.get('[data-test="morris-board"]', { timeout: 10000 }).should('be.visible').and('have.attr', 'data-variant', 'six');
      myTurn();
      return cy.wrap({ id, black });
    });
  });
}

/** Twelve pieces down, no mill for either side (White on the corners and 9, 13; Black on the middles and 11, 15), White to move. */
function toMovementWithoutMill(black: Scripted) {
  const white = [0, 2, 4, 6, 9, 13];
  const dark = [1, 3, 5, 7, 11, 15];
  white.forEach((w, i) => {
    click(w); owner(w, 'white');
    place(black, dark[i]); owner(dark[i], 'black');
    if (i < white.length - 1) myTurn();
  });
  myTurn();
  cy.get('[data-test="phase-hint"]').should('have.text', 'Move a piece');
}

describe("Six Men's Morris", () => {
  it('offers the variant in the lobby, with its rule', () => {
    cy.visit('/games/three-men-morris');
    cy.get('[data-test="name"]').type('Alice');
    cy.get('[data-test="new-game"]').click();
    cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
    cy.get('[data-test="variant-card-six"]').should('be.visible').and('contain.text', "Six Men's Morris").and('contain.text', '6 pieces each, mills');
    cy.get('[data-test="variant-card-six"]').click();
    cy.get('[data-test="option-variant-six"]').should('be.checked');
    cy.get('[data-test="variant-rule"]').should('contain.text', 'A mill takes a piece');
  });

  SIZES.forEach(([width, height]) => it(`plays placements, mills and removals at ${width}x${height}: only the pieces that can be taken answer, the lost counts follow, a win by two pieces left`, () => {
    cy.viewport(width, height);
    asWhiteInSix().then(({ black }) => {
      cy.get('[data-test="reserve-piece"]').should('have.length', 12);

      // White closes the outer top side (0 1 2) with its third piece and takes one of the two black pieces.
      cy.screenshot(`six-game-${width}x${height}-1-start`, { capture: 'viewport' });
      click(0); owner(0, 'white'); place(black, 12); owner(12, 'black'); myTurn();
      click(1); owner(1, 'white'); place(black, 13); owner(13, 'black'); myTurn();
      click(2); owner(2, 'white');
      cy.get('[data-test="removal-banner"]').should('be.visible');
      cy.get('[data-test="removal-title"]').should('have.text', 'Mill! Take one of Bob’s pieces.');
      cy.screenshot(`six-game-${width}x${height}-2-first-removal`, { capture: 'viewport' });
      cy.get('line[data-mill="true"]').should('have.length', 2); // the two half-sides of 0 1 2
      removable(12); removable(13);
      cy.get('[data-test="removal-ring"]').should('have.length', 2);
      cy.get('[data-test="shield"]').should('not.exist');
      // A white piece is not a piece to take: nothing happens, the banner stays.
      click(0);
      cy.get('[data-test="removal-banner"]').should('be.visible');
      owner(0, 'white');
      click(12);
      owner(12, 'none');
      cy.get('[data-test="removal-banner"]').should('not.exist');
      cy.get('[data-test="board-live"]').should('contain.text', 'Alice took the piece on');
      lost('black').should('contain.text', '1 lost');

      // Black rebuilds (12 13 14, the inner bottom side) and takes the only white piece outside the mill.
      place(black, 12); owner(12, 'black'); myTurn();
      click(8); owner(8, 'white');
      place(black, 14); owner(14, 'black');
      cy.get('[data-test="removal-title"]').should('have.text', 'Bob closed a mill');
      cy.get('[data-test="removal-sub"]').should('have.text', 'and is taking one of your pieces.');
      cy.get('[data-test="point-4"]').should('have.attr', 'data-state', 'idle'); // the board is not mine to act on
      take(black, 8);
      owner(8, 'none');
      cy.get('[data-test="removal-banner"]').should('not.exist');
      lost('white').should('contain.text', '1 lost');
      myTurn();

      // White closes 2 3 4 (sharing the corner 2). Black's mill is safe; the lone piece on 7 is the one to take.
      click(3); owner(3, 'white');
      place(black, 7); owner(7, 'black'); myTurn();
      click(4); owner(4, 'white');
      cy.get('[data-test="removal-banner"]').should('be.visible');
      removable(7);
      cy.get('[data-test="removal-ring"]').should('have.length', 1);
      [12, 13, 14].forEach(protectedPiece);
      cy.get('[data-test="shield"]').should('have.length', 3);
      cy.screenshot(`six-game-${width}x${height}-3-protected`, { capture: 'viewport' });
      cy.get('[data-test="removal-protected"]').should('contain.text', 'Pieces in a mill are safe while Bob has others.');
      // A protected piece cannot be clicked into the removal.
      click(12);
      cy.get('[data-test="removal-banner"]').should('be.visible');
      owner(12, 'black');
      click(7);
      owner(7, 'none');
      lost('black').should('contain.text', '2 lost');

      // The last black piece goes down; the movement phase begins with White to move.
      place(black, 15); owner(15, 'black');
      myTurn();
      cy.get('[data-test="phase-hint"]').should('have.text', 'Move a piece');
      cy.get('[data-test="reserve-piece"]').should('have.length', 0);

      // White opens the mill 2 3 4 through the join 3-11 and closes it again; Black shuffles out of the way.
      click(3); click(11); owner(11, 'white'); owner(3, 'none');
      move(black, 15, 7); owner(7, 'black'); myTurn();
      click(11); click(3); owner(3, 'white');
      cy.get('[data-test="removal-banner"]').should('be.visible');
      removable(7); [12, 13, 14].forEach(protectedPiece);
      click(7);
      owner(7, 'none');
      lost('black').should('contain.text', '3 lost');
      cy.get('[data-test="player-tag"][data-colour="black"] [data-test="tag-on-board"]').should('contain.text', '3 on board');

      // Black has three pieces, all in a mill (so all can be taken once it opens). One more mill ends the game.
      move(black, 14, 15); owner(15, 'black'); myTurn();
      click(3); click(11); owner(11, 'white');
      move(black, 15, 8); owner(8, 'black'); myTurn();
      click(11); click(3); owner(3, 'white');
      cy.get('[data-test="removal-banner"]').should('be.visible');
      [8, 12, 13].forEach(removable);
      cy.get('[data-test="shield"]').should('not.exist');
      click(8);
      cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
      cy.get('.end-title').should('have.text', 'VICTORY');
      cy.get('[data-test="end-winner"]').should('have.text', 'Winner: Alice');
      cy.get('.end-sub').should('have.text', 'Bob has only two pieces left.');
      cy.screenshot(`six-game-${width}x${height}-4-end`, { capture: 'viewport' });
      waitForFrame(black, (f) => f.state?.outcome?.status === 'FINISHED' && f.state.outcome.reason === 'REDUCED' && f.state.outcome.winner === 0);
    });
  }));

  it('as White I lose when Black takes me down to two pieces: defeat names the winner', () => {
    asWhiteInSix().then(({ black }) => {
      // Black closes 8 9 10 (the inner top side) with its third piece, later 12 13 14, then re-closes the second twice;
      // White never closes a mill (corners and inner middles only) and shuffles one piece between 0 and 7.
      click(0); owner(0, 'white'); place(black, 8); owner(8, 'black'); myTurn();
      click(2); owner(2, 'white'); place(black, 9); owner(9, 'black'); myTurn();
      click(4); owner(4, 'white'); place(black, 10); owner(10, 'black');
      cy.get('[data-test="removal-title"]').should('have.text', 'Bob closed a mill');
      take(black, 4); owner(4, 'none'); lost('white').should('contain.text', '1 lost'); myTurn();
      click(6); owner(6, 'white'); place(black, 12); owner(12, 'black'); myTurn();
      click(11); owner(11, 'white'); place(black, 13); owner(13, 'black'); myTurn();
      click(15); owner(15, 'white'); place(black, 14); owner(14, 'black');
      take(black, 11); owner(11, 'none'); lost('white').should('contain.text', '2 lost');
      myTurn();
      cy.get('[data-test="phase-hint"]').should('have.text', 'Move a piece');
      // Black opens 12 13 14 through the join 13-5 and closes it again, twice.
      click(0); click(7); owner(7, 'white');
      move(black, 13, 5); owner(5, 'black'); myTurn();
      click(7); click(0); owner(0, 'white');
      move(black, 5, 13); owner(13, 'black');
      cy.get('[data-test="removal-title"]').should('have.text', 'Bob closed a mill');
      take(black, 15); owner(15, 'none'); lost('white').should('contain.text', '3 lost');
      myTurn();
      cy.get('[data-test="last-pieces"]').should('contain.text', 'One more loss and Alice loses.');
      click(0); click(7); owner(7, 'white');
      move(black, 13, 5); owner(5, 'black'); myTurn();
      click(7); click(0); owner(0, 'white');
      move(black, 5, 13); owner(13, 'black');
      take(black, 6);
      cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
      cy.get('.end-title').should('have.text', 'DEFEAT');
      cy.get('[data-test="end-winner"]').should('have.text', 'Winner: Bob');
      cy.get('.end-sub').should('have.text', 'You have only two pieces left: Bob wins.');
    });
  });

  it('a draw by agreement ends the movement phase as a draw; no offer is possible while placing', () => {
    asWhiteInSix().then(({ black }) => {
      cy.get('[data-test="draw-offer"]').should('not.exist');
      toMovementWithoutMill(black);
      cy.get('[data-test="draw-offer"]').should('have.text', 'Offer a draw').and('not.be.disabled').click();
      waitForFrame(black, (f) => f.eventType === 'DRAW_OFFERED');
      send(black, 'acceptDraw');
      cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
      cy.get('.end-title').should('have.text', 'DRAW');
      cy.get('[data-test="end-winner"]').should('not.exist');
      cy.get('.end-sub').should('have.text', 'Draw agreed.');
    });
  });

  SIZES.forEach(([width, height]) => {
    it(`lays the Six table out at ${width}x${height} with a removal pending: no scroll, inside the screen, no overlap`, () => {
      cy.viewport(width, height);
      asWhiteInSix().then(({ black }) => {
        click(0); owner(0, 'white'); place(black, 12); owner(12, 'black'); myTurn();
        click(1); owner(1, 'white'); place(black, 13); owner(13, 'black'); myTurn();
        click(2); owner(2, 'white');
        cy.get('[data-test="removal-banner"]').should('be.visible');
        cy.screenshot(`six-${width}x${height}-removal`, { capture: 'viewport' });
        cy.document().then((doc) => {
          const el = doc.scrollingElement as Element;
          expect(el.scrollWidth, 'no horizontal scroll').to.be.at.most(el.clientWidth + 1);
          expect(el.scrollHeight, 'no vertical scroll').to.be.at.most(el.clientHeight + 1);
          const sel: Record<string, string> = {
            board: '[data-test="morris-board"]', tags: '[data-test="player-tag"]', banner: '[data-test="removal-banner"]',
            leave: '[data-test="leave"]',
          };
          const boxes = Object.entries(sel).flatMap(([name, s]) => [...doc.querySelectorAll(s)]
            .filter((e) => e.getClientRects().length > 0)
            .map((e, i) => ({ name: `${name}#${i}`, r: e.getBoundingClientRect() })));
          expect(boxes.length).to.be.at.least(5);
          boxes.forEach(({ name, r }) => {
            expect(r.left, `${name} left`).to.be.at.least(-1);
            expect(r.top, `${name} top`).to.be.at.least(-1);
            expect(r.right, `${name} right`).to.be.at.most(width + 1);
            expect(r.bottom, `${name} bottom`).to.be.at.most(height + 1);
          });
          for (let i = 0; i < boxes.length; i++) {
            for (let j = i + 1; j < boxes.length; j++) {
              const a = boxes[i].r;
              const b = boxes[j].r;
              const hit = a.left < b.right - 0.5 && b.left < a.right - 0.5 && a.top < b.bottom - 0.5 && b.top < a.bottom - 0.5;
              expect(hit, `${boxes[i].name} and ${boxes[j].name} must not overlap`).to.eq(false);
            }
          }
          for (let n = 0; n < 16; n++) {
            const r = doc.querySelector(`[data-test="point-${n}"]`)!.getBoundingClientRect();
            expect(r.width, `point ${n} width`).to.be.at.least(44);
            expect(r.height, `point ${n} height`).to.be.at.least(44);
          }
        });
        click(12);
        owner(12, 'none');
      });
    });
  });
});
