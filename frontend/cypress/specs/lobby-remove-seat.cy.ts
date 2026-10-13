// The host removes a human seat from the lobby (connected or not); a full or started lobby says so on join.
import { connectScriptedPlayer, joinAsScriptedPlayer } from '../support/stompPlayer';

type Kind = 'bullshit' | 'bataillecorse';
const joinPath = (kind: Kind, id: string) => (kind === 'bullshit' ? `/api/bullshit/game/${id}/join` : `/api/game/${id}/join`);
const tokenKey = (kind: Kind, id: string) => (kind === 'bullshit' ? `bullshit:tokens:${id}` : `tokens:${id}`);

/** A lobby created over REST; the spec holds the host token. */
function createRoom(kind: Kind) {
  return cy.request('POST', '/api/lobby', { gameType: kind === 'bullshit' ? 'bullshit' : 'bataille-corse', name: 'Alice' })
    .then((res) => res.body as { gameId: string; token: string });
}

/** Opens the room page as the holder of `token` at `seat`. */
function visitRoomAs(kind: Kind, id: string, seat: number, token: string) {
  cy.visit(`/games/${kind}/room/${id}`, {
    onBeforeLoad: (win) => win.localStorage.setItem(tokenKey(kind, id), JSON.stringify({ [seat]: token })),
  });
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
}

/**
 * A view sent between the browser's first load and its seat subscription is lost, and the lobby carries no
 * other signal that the channel is live. So `publish` (a repeatable change that shows up in the lobby, such as a
 * rename) is replayed with a fresh label until the browser displays it: from then on the channel is live.
 */
function untilBrowserSees(publish: (label: string) => void) {
  return cy.window({ log: false }).then((win) => new Promise<void>((resolve, reject) => {
    let attempt = 0;
    const next = () => {
      if (attempt >= 12) { reject(new Error('the browser never received the lobby update')); return; }
      const label = `Nudge${++attempt}`;
      publish(label);
      const deadline = Date.now() + 1500;
      const look = () => {
        if (win.document.querySelector('.players')?.textContent?.includes(label)) { resolve(); return; }
        if (Date.now() > deadline) { next(); return; }
        setTimeout(look, 50);
      };
      look();
    };
    next();
  }));
}

function noHorizontalScroll() {
  cy.document().then((doc) => {
    expect(doc.documentElement.scrollWidth, 'page width').to.be.at.most(doc.documentElement.clientWidth);
  });
}

describe('Host removes a seat', () => {
  [375, 1280].forEach((width) => {
    it(`removes a connected guest from the Bullshit lobby at ${width} px; the guest is told`, () => {
      cy.viewport(width, width === 375 ? 667 : 800);
      createRoom('bullshit').then(({ gameId, token }) => {
        visitRoomAs('bullshit', gameId, 0, token);
        joinAsScriptedPlayer(gameId, 'Bob').then((bob) => {
          untilBrowserSees((label) => bob.publish('/app/lobby/rename', { name: label }));
          cy.get('[data-test="player-count"]').should('contain.text', '2 / 6');
          cy.get('[data-test="remove-seat-0"]').should('not.exist');
          cy.get('[data-test="remove-seat-1"]').should('have.attr', 'aria-label', 'Remove Nudge1');
          noHorizontalScroll();
          cy.get('[data-test="remove-seat-1"]').click();
          cy.get('[data-test="player-count"]').should('contain.text', '1 / 6');
          cy.get('.players li').should('have.length', 1);
          cy.wrap(null).should(() => {
            const notice = bob.frames.find((f) => f.eventType === 'REMOVED');
            expect(notice?.message).to.eq('You were removed from the lobby by the host');
          });
          noHorizontalScroll();
        });
      });
    });
  });

  it('removes a guest from the Bataille Corse lobby', () => {
    createRoom('bataillecorse').then(({ gameId, token }) => {
      visitRoomAs('bataillecorse', gameId, 0, token);
      cy.request('POST', joinPath('bataillecorse', gameId), { name: 'Bob' }).then((res) => {
        connectScriptedPlayer(gameId, res.body.token, res.body.playerId).then((bob) => {
          untilBrowserSees((label) => bob.publish('/app/lobby/rename', { name: label }));
        });
        cy.get('[data-test="player-count"]').should('contain.text', '2 / 2');
        cy.get('[data-test="remove-seat-1"]').click();
        cy.get('[data-test="player-count"]').should('contain.text', '1 / 2');
      });
    });
  });

  it('flags a guest whose connection dropped as offline, and the host can still remove them', () => {
    createRoom('bullshit').then(({ gameId, token }) => {
      visitRoomAs('bullshit', gameId, 0, token);
      joinAsScriptedPlayer(gameId, 'Bob').then((bob) => {
        untilBrowserSees((label) => bob.publish('/app/lobby/rename', { name: label }));
        bob.publish('/app/presence', {});
        cy.get('.players li').eq(1).find('[data-test="badge-offline"]').should('not.exist');
        cy.then(() => bob.disconnect());
        cy.get('.players li').eq(1).find('[data-test="badge-offline"]').should('have.text', 'offline');
        cy.get('[data-test="remove-seat-1"]').click();
        cy.get('.players li').should('have.length', 1);
      });
    });
  });

  [375, 1280].forEach((width) => {
    it(`sends a removed player to the Game selection with a notice at ${width} px`, () => {
      cy.viewport(width, width === 375 ? 667 : 800);
      createRoom('bullshit').then(({ gameId, token: hostToken }) => {
        cy.request('POST', joinPath('bullshit', gameId), { name: 'Bob' }).then((res) => {
          connectScriptedPlayer(gameId, hostToken, 0).then((host) => {
            visitRoomAs('bullshit', gameId, res.body.playerId, res.body.token);
            untilBrowserSees((label) => host.publish('/app/lobby/rename', { name: label }));
            cy.get('.players li').should('have.length', 2);
            cy.then(() => host.publish('/app/lobby/removeSeat', { seat: 1 }));
            cy.location('pathname', { timeout: 10000 }).should('eq', '/games');
            cy.get('[data-test="removed-notice"]').should('have.text', 'You were removed from the lobby by the host.');
            cy.window().then((win) => expect(win.localStorage.getItem(tokenKey('bullshit', gameId))).to.eq(null));
            noHorizontalScroll();
          });
        });
      });
    });
  });

  it('refuses a removal sent by a guest, and keeps everybody seated', () => {
    createRoom('bullshit').then(({ gameId, token }) => {
      visitRoomAs('bullshit', gameId, 0, token);
      joinAsScriptedPlayer(gameId, 'Bob').then((bob) => {
        joinAsScriptedPlayer(gameId, 'Carol').then((carol) => {
          untilBrowserSees((label) => carol.publish('/app/lobby/rename', { name: label }));
        });
        cy.get('[data-test="player-count"]').should('contain.text', '3 / 6');
        cy.then(() => bob.publish('/app/lobby/removeSeat', { seat: 2 }));
        cy.wrap(null).should(() => {
          expect(bob.frames.some((f) => f.success === false)).to.eq(true);
        });
        cy.get('[data-test="player-count"]').should('contain.text', '3 / 6');
      });
    });
  });
});

describe('Joining a lobby that cannot take you', () => {
  function tryJoin(kind: Kind, id: string) {
    cy.visit(`/games/${kind}/join/${id}`);
    cy.get('[data-test="submit"]').click();
  }

  it('says a Bullshit lobby is full', () => {
    createRoom('bullshit').then(({ gameId }) => {
      for (let i = 1; i < 6; i++) cy.request('POST', joinPath('bullshit', gameId), { name: `P${i}` });
      tryJoin('bullshit', gameId);
      cy.get('[data-test="join-error"]').should('have.text', 'This lobby is full.');
      cy.location('pathname').should('eq', `/games/bullshit/join/${gameId}`);
    });
  });

  it('says a Bataille Corse lobby is full', () => {
    createRoom('bataillecorse').then(({ gameId }) => {
      cy.request('POST', joinPath('bataillecorse', gameId), { name: 'Bob' });
      tryJoin('bataillecorse', gameId);
      cy.get('[data-test="join-error"]').should('have.text', 'This lobby is full.');
    });
  });

  it('says a Bataille Corse game has already started', () => {
    createRoom('bataillecorse').then(({ gameId, token }) => {
      cy.request('POST', joinPath('bataillecorse', gameId), { name: 'Bob' });
      connectScriptedPlayer(gameId, token, 0).then((host) => {
        cy.then(() => host.publish('/app/bataillecorse/start', {}));
        cy.request(`/api/game/${gameId}?token=${token}`).its('body.started').should('not.eq', false);
        tryJoin('bataillecorse', gameId);
        cy.get('[data-test="join-error"]').should('have.text', 'This game has already started.');
      });
    });
  });

  it('says a Bullshit game has already started', () => {
    createRoom('bullshit').then(({ gameId, token }) => {
      cy.request('POST', joinPath('bullshit', gameId), { name: 'Bob' });
      connectScriptedPlayer(gameId, token, 0).then((host) => {
        cy.then(() => host.publish('/app/bullshit/start', {}));
        cy.request(`/api/bullshit/game/${gameId}?token=${token}`).its('body.started').should('eq', true);
        tryJoin('bullshit', gameId);
        cy.get('[data-test="join-error"]').should('have.text', 'This game has already started.');
      });
    });
  });

  it('still says no game found for an unknown id', () => {
    tryJoin('bullshit', '00000000-0000-0000-0000-000000000000');
    cy.get('[data-test="join-error"]').should('contain.text', 'No game found');
  });
});
