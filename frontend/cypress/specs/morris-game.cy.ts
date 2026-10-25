// Three Men's Morris, played end to end. The browser is one player, a scripted STOMP client the other; every wait is
// on a DOM state or on a frame, never on a delay. Points are numbered row by row (0 1 2 / 3 4 5 / 6 7 8, centre 4).
// White (seat 0) always starts. A line made while placing wins at once; a blocked player loses (unreachable on this
// board, so that ending is covered by the unit tests).
import { connectScriptedPlayer, joinAsScriptedPlayer, type ScriptedPlayer } from '../support/stompPlayer';

type Scripted = ScriptedPlayer & { token: string; seat: number };

const place = (p: Scripted, point: number) => cy.then(() => p.publish('/app/three-men-morris/place', { point }));
const move = (p: Scripted, from: number, to: number) => cy.then(() => p.publish('/app/three-men-morris/move', { from, to }));
const owner = (n: number, who: 'white' | 'black' | 'none') => cy.get(`[data-test="point-${n}"]`).should('have.attr', 'data-owner', who);
const myTurn = () => cy.get('[data-test="turn-hint"]').should('contain.text', 'Your turn');
const click = (n: number) => cy.get(`[data-test="point-${n}"]`).click();

/** The browser as White (host, through the UI) against a scripted Black; the game is started. */
function asWhite(): Cypress.Chainable<{ id: string; black: Scripted }> {
  cy.visit('/three-men-morris');
  cy.get('[data-test="name"]').type('Alice');
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/three-men-morris\/room\/.+/);
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  // The host colour defaults to random: this spec needs the host to be White.
  cy.get('[data-test="colour-white"]').click();
  cy.get('[data-test="option-hostColour-white"]').should('be.checked');
  return cy.location('pathname').then((path) => {
    const id = path.split('/room/')[1];
    return joinAsScriptedPlayer(id, 'Bob', 'three-men-morris').then((black) => {
      cy.get('[data-test="start"]').should('not.be.disabled').click();
      cy.get('[data-test="morris-board"]', { timeout: 10000 }).should('be.visible');
      cy.get('[data-test="lobby"]').should('not.exist');
      myTurn();
      return cy.wrap({ id, black });
    });
  });
}

/** The browser as Black (guest seat 1) against a scripted White host, which starts the game. */
function asBlack(): Cypress.Chainable<{ id: string; white: Scripted }> {
  return cy.request('POST', '/api/lobby', { gameType: 'three-men-morris', name: 'Alice' }).then((created) => {
    const { gameId, token } = created.body as { gameId: string; token: string };
    return cy.request('POST', `/api/three-men-morris/game/${gameId}/join`, { name: 'Bob' }).then((joined) => {
      const guest = joined.body as { playerId: number; token: string };
      cy.visit(`/three-men-morris/room/${gameId}`, {
        onBeforeLoad: (win) => win.localStorage.setItem(`three-men-morris:tokens:${gameId}`, JSON.stringify({ [guest.playerId]: guest.token })),
      });
      cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
      return connectScriptedPlayer(gameId, token, 0).then((white) => {
        // The host colour defaults to random: the scripted host picks White, then starts (same connection, so in order).
        cy.then(() => white.publish('/app/lobby/setOptions', { values: { hostColour: 'white' } }));
        cy.then(() => white.publish('/app/three-men-morris/start', {}));
        cy.get('[data-test="morris-board"]', { timeout: 10000 }).should('be.visible');
        return cy.wrap({ id: gameId, white });
      });
    });
  });
}

describe("Three Men's Morris game", () => {
  it('places pieces in turn, wins on a line while placing, holds the line, then Play again leads to the lobby', () => {
    asWhite().then(({ black }) => {
      cy.get('[data-test="colour-hint"]').should('contain.text', 'You play White. White starts.');
      cy.get('[data-test="phase-hint"]').should('have.text', 'Place a piece');
      cy.get('[data-test="point-4"]').should('have.attr', 'data-state', 'highlighted');
      click(0); owner(0, 'white');
      cy.get('[data-test="turn-hint"]').should('not.exist');
      cy.get('[data-test="phase-hint"]').should('contain.text', 'Waiting for Bob');
      cy.get('[data-test="colour-hint"]').should('not.exist');
      place(black, 3); owner(3, 'black'); myTurn();
      click(1); owner(1, 'white');
      place(black, 4); owner(4, 'black'); myTurn();
      cy.get('[data-test="board-live"]').should('contain.text', 'Bob placed a piece on Centre.');
      cy.get('[data-test="tag-reserve"]').last().should('have.attr', 'aria-label', '1 piece left to place');
      click(2); owner(2, 'white');
      // The line is drawn first, the end screen follows.
      cy.get('line[data-winning="true"]').should('have.attr', 'data-line', '0-1-2');
      cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
      cy.get('.end-title').should('have.text', 'VICTORY');
      cy.get('[data-test="end-winner"]').should('have.text', 'Winner: Alice');
      cy.get('.end-sub').should('have.text', 'You lined up three.');
      cy.get('[data-cy="play-again"]').click();
      cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
    });
  });

  it('as Black: the opponent starts, I win on a line and the end screen names me', () => {
    asBlack().then(({ white }) => {
      cy.get('[data-test="turn-hint"]').should('not.exist');
      cy.get('[data-test="phase-hint"]').should('contain.text', 'Waiting for');
      cy.get('[data-test="colour-hint"]').should('contain.text', 'You play Black');
      place(white, 0); owner(0, 'white'); myTurn();
      click(3); owner(3, 'black');
      place(white, 2); owner(2, 'white'); myTurn();
      click(4); owner(4, 'black');
      place(white, 7); owner(7, 'white'); myTurn();
      click(5); owner(5, 'black');
      cy.get('line[data-winning="true"]').should('have.attr', 'data-line', '3-4-5');
      cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
      cy.get('.end-title').should('have.text', 'VICTORY');
      cy.get('[data-test="end-winner"]').should('have.text', 'Winner: Bob');
    });
  });

  it('as White I lose when Black lines up three: defeat names the winner', () => {
    asWhite().then(({ black }) => {
      click(0); owner(0, 'white'); place(black, 3); owner(3, 'black'); myTurn();
      click(2); owner(2, 'white'); place(black, 4); owner(4, 'black'); myTurn();
      click(7); owner(7, 'white'); place(black, 5); owner(5, 'black');
      cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
      cy.get('.end-title').should('have.text', 'DEFEAT');
      cy.get('[data-test="end-winner"]').should('have.text', 'Winner: Bob');
      cy.get('.end-sub').should('have.text', 'Bob lined up three.');
    });
  });

  describe('movement', () => {
    function toMovement(black: Scripted) {
      click(0); owner(0, 'white'); place(black, 1); owner(1, 'black'); myTurn();
      click(2); owner(2, 'white'); place(black, 3); owner(3, 'black'); myTurn();
      click(7); owner(7, 'white'); place(black, 5); owner(5, 'black'); myTurn();
      cy.get('[data-test="phase-hint"]').should('have.text', 'Move a piece');
    }

    it('highlights my movable pieces, then only the destinations of the selected one; Escape clears; a move is played', () => {
      asWhite().then(({ black }) => {
        toMovement(black);
        // Free points are 4, 6 and 8. Pieces 0, 2 and 7 can move; 1, 3 and 5 belong to Black.
        [0, 2, 7].forEach((n) => cy.get(`[data-test="point-${n}"]`).should('have.attr', 'data-state', 'highlighted'));
        [1, 3, 5, 4, 6, 8].forEach((n) => cy.get(`[data-test="point-${n}"]`).should('have.attr', 'data-state', 'idle'));
        click(7);
        cy.get('[data-test="point-7"]').should('have.attr', 'data-state', 'selected').and('have.attr', 'aria-pressed', 'true');
        [4, 6, 8].forEach((n) => cy.get(`[data-test="point-${n}"]`).should('have.attr', 'data-state', 'highlighted'));
        cy.get('[data-test="point-7"]').type('{esc}');
        cy.get('[data-test="point-7"]').should('have.attr', 'data-state', 'highlighted');
        [4, 6, 8].forEach((n) => cy.get(`[data-test="point-${n}"]`).should('have.attr', 'data-state', 'idle'));
        click(2);
        cy.get('[data-test="point-2"]').should('have.attr', 'data-state', 'selected');
        cy.get('[data-test="point-4"]').should('have.attr', 'data-state', 'highlighted');
        cy.get('[data-test="point-6"]').should('have.attr', 'data-state', 'idle');
        click(7); // changes the selection
        cy.get('[data-test="point-7"]').should('have.attr', 'data-state', 'selected');
        click(6);
        owner(6, 'white'); owner(7, 'none');
        cy.get('[data-test="turn-hint"]').should('not.exist');
        move(black, 5, 8);
        owner(8, 'black'); owner(5, 'none'); myTurn();
        cy.get('[data-test="board-live"]').should('contain.text', 'Bob moved from Middle right to Bottom right.');
      });
    });
  });

  it('is operable with the keyboard alone: arrows move between points, Enter places', () => {
    asWhite().then(() => {
      cy.get('[data-test="morris-board"] button[tabindex="0"]').should('have.length', 1);
      cy.get('[data-test="point-0"]').focus().type('{rightarrow}');
      cy.get('[data-test="point-1"]').should('have.focus');
      cy.get('[data-test="point-1"]').type('{downarrow}');
      cy.get('[data-test="point-4"]').should('have.focus').type('{enter}');
      owner(4, 'white');
      cy.get('[data-test="point-4"]').should('have.focus');
    });
  });

  describe('leaving', () => {
    it('Back during the game asks, resigns, and tells the opponent', () => {
      asWhite().then(({ black }) => {
        click(0); owner(0, 'white');
        cy.on('window:confirm', (text) => { expect(text).to.contain('forfeit'); return true; });
        cy.get('[data-test="leave"]').click();
        cy.location('pathname').should('eq', '/');
        cy.wrap(null).should(() => {
          const frame = black.frames.find((f) => f.eventType === 'FORFEIT');
          expect(frame, 'Black got the forfeit').to.exist;
          expect((frame as any).eventData.reason).to.eq('RESIGNED');
        });
      });
    });

    it('the opponent resigning ends my game: victory, and the subtitle says who resigned', () => {
      asBlack().then(({ white }) => {
        place(white, 0); owner(0, 'white'); myTurn();
        cy.then(() => white.publish('/app/forfeit', {}));
        cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
        cy.get('.end-title').should('have.text', 'VICTORY');
        cy.get('.end-sub').invoke('text').should('match', /^Alice resigned\.$/);
      });
    });

    it('a disconnected opponent shows the countdown, and coming back clears it', () => {
      asBlack().then(({ id, white }) => {
        // The server tracks a seat only once it has announced its presence, as the app does.
        cy.then(() => white.publish('/app/presence', {}));
        place(white, 0); owner(0, 'white'); myTurn();
        cy.then(() => white.disconnect());
        cy.get('[data-cy="disconnect-overlay"]', { timeout: 15000 }).should('contain.text', 'disconnected');
        cy.get('[data-cy="disconnect-countdown"]').should('contain.text', 'You win in');
        cy.then(() => connectScriptedPlayer(id, white.token, 0)).then((back) => back.publish('/app/presence', {}));
        cy.get('[data-cy="disconnect-overlay"]', { timeout: 15000 }).should('not.exist');
      });
    });

    it('an opponent who stays away forfeits after the grace period', { defaultCommandTimeout: 90000 }, () => {
      asBlack().then(({ white }) => {
        cy.then(() => white.publish('/app/presence', {}));
        place(white, 0); owner(0, 'white'); myTurn();
        cy.then(() => white.disconnect());
        cy.get('[data-cy="disconnect-overlay"]', { timeout: 15000 }).should('be.visible');
        cy.get('[data-test="end"]', { timeout: 90000 }).should('be.visible');
        cy.get('.end-title').should('have.text', 'VICTORY');
        cy.get('.end-sub').invoke('text').should('match', /^Alice disconnected\.$/);
      });
    });
  });

  it('survives a reload in the middle of the game: the board, the turn and the seat come back', () => {
    asWhite().then(({ black }) => {
      click(0); owner(0, 'white'); place(black, 3); owner(3, 'black'); myTurn();
      cy.reload();
      cy.get('[data-test="morris-board"]', { timeout: 10000 }).should('be.visible');
      owner(0, 'white'); owner(3, 'black');
      myTurn();
      cy.get('[data-test="player-tag"][data-colour="white"] [data-test="tag-name"]').should('have.text', 'Alice');
      click(4); owner(4, 'white');
    });
  });

  [[375, 667], [1280, 800]].forEach(([width, height]) => {
    it(`lays the table out at ${width}x${height}: no scroll, inside the screen, no overlap, targets of 44 px`, () => {
      cy.viewport(width, height);
      asWhite().then(({ black }) => {
        click(0); owner(0, 'white'); place(black, 3); owner(3, 'black'); myTurn();
        cy.document().then((doc) => {
          const el = doc.scrollingElement as Element;
          expect(el.scrollWidth, 'no horizontal scroll').to.be.at.most(el.clientWidth + 1);
          expect(el.scrollHeight, 'no vertical scroll').to.be.at.most(el.clientHeight + 1);
          const sel: Record<string, string> = {
            timer: '[data-cy="game-timer"]', rules: '[data-cy="rules-toggle"]', board: '[data-test="morris-board"]',
            tags: '[data-test="player-tag"]', turn: '[data-test="turn-hint"]', phase: '[data-test="phase-hint"]', leave: '[data-test="leave"]',
          };
          const boxes = Object.entries(sel).flatMap(([name, s]) => [...doc.querySelectorAll(s)]
            .filter((e) => e.getClientRects().length > 0)
            .map((e, i) => ({ name: `${name}#${i}`, r: e.getBoundingClientRect() })));
          expect(boxes.length).to.be.at.least(8);
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
          for (let n = 0; n < 9; n++) {
            const r = doc.querySelector(`[data-test="point-${n}"]`)!.getBoundingClientRect();
            expect(r.width, `point ${n} width`).to.be.at.least(44);
            expect(r.height, `point ${n} height`).to.be.at.least(44);
          }
          const leave = doc.querySelector('[data-test="leave"] button')!.getBoundingClientRect();
          expect(leave.height, 'Back height').to.be.at.least(44);
        });
        cy.get('[data-cy="rules-toggle"]').click();
        cy.get('[data-cy="rules-panel"]').should('be.visible').and('contain.text', 'cannot move');
      });
    });
  });
});
