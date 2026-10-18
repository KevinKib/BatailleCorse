// Three Men's Morris variants and colours, end to end. The browser is one player, a scripted STOMP client the other;
// every wait is on a DOM state or on a frame, never on a delay. Points are numbered row by row (0 1 2 / 3 4 5 / 6 7 8,
// centre 4). The host picks the variant and the colour in the lobby; the guest reads them; White always starts.
import { connectScriptedPlayer, joinAsScriptedPlayer, waitForFrame, type ScriptedPlayer } from '../support/stompPlayer';

type Scripted = ScriptedPlayer & { token: string; seat: number };

const place = (p: Scripted, point: number) => cy.then(() => p.publish('/app/three-men-morris/place', { point }));
const owner = (n: number, who: 'white' | 'black' | 'none') => cy.get(`[data-test="point-${n}"]`).should('have.attr', 'data-owner', who);
const myTurn = () => cy.get('[data-test="turn-hint"]').should('contain.text', 'Your turn');
const click = (n: number) => cy.get(`[data-test="point-${n}"]`).click();
const SIZES: Array<[number, number]> = [[375, 667], [1280, 800]];

/** The browser as the host, through the UI, in a lobby with a scripted guest (seat 1) who has joined. */
function hostLobbyWithGuest(): Cypress.Chainable<{ id: string; guest: Scripted }> {
  cy.visit('/games/three-men-morris');
  cy.get('[data-test="name"]').type('Alice');
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/three-men-morris\/room\/.+/);
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  return cy.location('pathname').then((path) => {
    const id = path.split('/room/')[1];
    return joinAsScriptedPlayer(id, 'Bob', 'three-men-morris').then((guest) => cy.wrap({ id, guest }));
  });
}

/** The browser as the guest (seat 1), against a scripted host who sets the options and later starts the game. */
function guestLobbyWithHost(): Cypress.Chainable<{ id: string; host: Scripted }> {
  return cy.request('POST', '/api/lobby', { gameType: 'three-men-morris', name: 'Alice' }).then((created) => {
    const { gameId, token } = created.body as { gameId: string; token: string };
    return cy.request('POST', `/api/three-men-morris/game/${gameId}/join`, { name: 'Bob' }).then((joined) => {
      const guest = joined.body as { playerId: number; token: string };
      cy.visit(`/games/three-men-morris/room/${gameId}`, {
        onBeforeLoad: (win) => win.localStorage.setItem(`three-men-morris:tokens:${gameId}`, JSON.stringify({ [guest.playerId]: guest.token })),
      });
      cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
      return connectScriptedPlayer(gameId, token, 0).then((host) => cy.wrap({ id: gameId, host }));
    });
  });
}

describe("Three Men's Morris variants and colours", () => {
  it('the host picks Achi and Black, the guest reads both, the guest (White) places first, 4 pieces each', () => {
    hostLobbyWithGuest().then(({ guest }) => {
      cy.get('[data-test="option-variant"]').should('be.visible');
      cy.get('[data-test="variant-card-picaria"]').should('not.exist');
      cy.get('[data-test^="variant-card-"]').should('have.length', 3);
      ['tapatan', 'achi', 'six'].forEach((key) => cy.get(`[data-test="variant-card-${key}"]`).should('be.visible'));
      cy.get('[data-test="option-hostColour-random"]').should('be.checked');
      cy.get('[data-test="variant-rule"]').should('contain.text', 'Tapatan');
      cy.get('[data-test="variant-card-achi"]').click();
      cy.get('[data-test="option-variant-achi"]').should('be.checked');
      cy.get('[data-test="variant-rule"]').should('contain.text', 'one point stays free');
      cy.get('[data-test="colour-black"]').click();
      cy.get('[data-test="option-hostColour-black"]').should('be.checked');
      // The guest is told, read only: the lobby it was sent holds both choices.
      waitForFrame(guest, (f) => f.state?.options?.variant === 'achi' && f.state?.options?.hostColour === 'black');

      cy.get('[data-test="start"]').should('not.be.disabled').click();
      cy.get('[data-test="morris-board"]', { timeout: 10000 }).should('be.visible');
      cy.get('[data-test="variant-name"]').should('have.text', 'Achi');
      cy.get('[data-test="colour-hint"]').should('contain.text', 'You play Black');
      cy.get('[data-test="player-tag"]').first().should('have.attr', 'data-colour', 'white');
      cy.get('[data-test="player-tag"]').last().should('have.attr', 'data-colour', 'black');
      cy.get('[data-test="reserve-piece"]').should('have.length', 8);
      cy.get('[data-test="phase-hint"]').should('contain.text', 'Waiting for Bob');
      cy.get('[data-test="turn-hint"]').should('not.exist');

      // White (the guest) places first. 0 1 5 6 for White, 4 2 7 8 for Black: no line, point 3 stays free.
      const white = [0, 1, 5, 6];
      const black = [4, 2, 7, 8];
      white.forEach((w, i) => {
        place(guest, w);
        owner(w, 'white');
        myTurn();
        if (i > 0) cy.get('[data-test="phase-hint"]').should('have.text', 'Place a piece');
        click(black[i]);
        owner(black[i], 'black');
      });
      cy.get('[data-test="reserve-piece"]').should('have.length', 0);
      white.forEach((w) => owner(w, 'white'));
      black.forEach((b) => owner(b, 'black'));
      owner(3, 'none');
      // Placement is over: White (the guest) moves, so the host waits.
      cy.get('[data-test="phase-hint"]').should('contain.text', 'Waiting for Bob');
    });
  });

  it('the host picks Six and Black, the guest reads both, the guest (White) places first, 6 pieces each, a 16-point board', () => {
    hostLobbyWithGuest().then(({ guest }) => {
      cy.get('[data-test="variant-card-six"]').click();
      cy.get('[data-test="option-variant-six"]').should('be.checked');
      cy.get('[data-test="variant-rule"]').should('contain.text', 'A mill takes a piece');
      cy.get('[data-test="colour-black"]').click();
      cy.get('[data-test="option-hostColour-black"]').should('be.checked');
      waitForFrame(guest, (f) => f.state?.options?.variant === 'six' && f.state?.options?.hostColour === 'black');

      cy.get('[data-test="start"]').should('not.be.disabled').click();
      cy.get('[data-test="morris-board"]', { timeout: 10000 }).should('be.visible').and('have.attr', 'data-variant', 'six');
      cy.get('[data-test="variant-name"]').should('have.text', "Six Men's Morris");
      cy.get('[data-test="colour-hint"]').should('contain.text', 'You play Black');
      cy.get('[data-test^="point-"]').should('have.length', 16);
      cy.get('[data-test="reserve-piece"]').should('have.length', 12);
      cy.get('[data-test="phase-hint"]').should('contain.text', 'Waiting for Bob');
      place(guest, 0);
      owner(0, 'white');
      myTurn();
      click(8);
      owner(8, 'black');
      cy.get('[data-test="reserve-piece"]').should('have.length', 10);
    });
  });

  it('shows the guest the variant and the colour read only, then the table with the right colours', () => {
    guestLobbyWithHost().then(({ host }) => {
      // A view sent before the browser's seat subscription is live is lost: repeat the (idempotent) change until it shows.
      cy.window({ log: false }).then({ timeout: 15000 }, (win) => new Promise<void>((resolve, reject) => {
        const deadline = Date.now() + 10000;
        const attempt = () => {
          host.publish('/app/lobby/setOptions', { values: { variant: 'achi', hostColour: 'black' } });
          setTimeout(() => {
            if (win.document.querySelector('[data-test="option-variant"]')?.textContent?.includes('Achi')) resolve();
            else if (Date.now() > deadline) reject(new Error('the guest never saw the options'));
            else attempt();
          }, 400);
        };
        attempt();
      }));
      cy.get('[data-test="option-variant"]').should('contain.text', 'Achi');
      cy.get('[data-test="option-hostColour"]').should('contain.text', 'Host plays').and('contain.text', 'Black');
      cy.get('[data-test="game-options"] input').should('not.exist');
      cy.get('[data-test="game-options"] button').should('not.exist');
      cy.get('[data-test="variant-rule"]').should('contain.text', 'one point stays free');

      cy.then(() => host.publish('/app/three-men-morris/start', {}));
      cy.get('[data-test="morris-board"]', { timeout: 10000 }).should('be.visible');
      cy.get('[data-test="variant-name"]').should('have.text', 'Achi');
      // The guest is White and starts.
      cy.get('[data-test="colour-hint"]').should('contain.text', 'You play White');
      myTurn();
      click(4);
      owner(4, 'white');
      cy.get('[data-test="reserve-piece"]').should('have.length', 7);
    });
  });

  SIZES.forEach(([width, height]) => {
    it(`lobby options and Achi board fit at ${width}x${height} with 44 px targets`, () => {
      cy.viewport(width, height);
      hostLobbyWithGuest().then(({ guest }) => {
        const noHorizontalScroll = () => cy.document().then((doc) => {
          expect(doc.documentElement.scrollWidth, 'page width').to.be.at.most(doc.documentElement.clientWidth);
        });
        cy.get('[data-test="variant-card-tapatan"]').scrollIntoView();
        const rects: Record<string, DOMRect> = {};
        ['tapatan', 'achi', 'six'].forEach((key) => {
          cy.get(`[data-test="variant-card-${key}"]`).should('be.visible').then(($el) => { rects[key] = $el[0].getBoundingClientRect(); });
        });
        cy.then(() => {
          // Three columns on one row, from a phone up; every card is a target of at least 44 px.
          expect(rects.tapatan.top).to.eq(rects.achi.top);
          expect(rects.achi.top).to.eq(rects.six.top);
          Object.values(rects).forEach((r) => {
            expect(r.height, 'card height').to.be.at.least(44);
            expect(r.width, 'card width').to.be.at.least(44);
          });
        });
        ['white', 'black', 'random'].forEach((colour) => {
          cy.get(`[data-test="colour-${colour}"]`).scrollIntoView().should('be.visible').then(($el) => {
            const r = $el[0].getBoundingClientRect();
            expect(r.height, `${colour} height`).to.be.at.least(44);
            expect(r.width, `${colour} width`).to.be.at.least(44);
          });
        });
        noHorizontalScroll();

        cy.get('[data-test="variant-card-achi"]').click();
        waitForFrame(guest, (f) => f.state?.options?.variant === 'achi');
        cy.get('[data-test="start"]').scrollIntoView().should('not.be.disabled').click();
        cy.get('[data-test="morris-board"]', { timeout: 10000 }).should('be.visible');
        for (let n = 0; n < 9; n++) {
          cy.get(`[data-test="point-${n}"]`).then(($el) => {
            const r = $el[0].getBoundingClientRect();
            expect(r.width, `point ${n} width`).to.be.at.least(44);
            expect(r.height, `point ${n} height`).to.be.at.least(44);
          });
        }
        cy.get('[data-test="variant-name"]').should('be.visible');
        cy.get('[data-test="leave"]').should('be.visible');
        noHorizontalScroll();
      });
    });
  });
});
