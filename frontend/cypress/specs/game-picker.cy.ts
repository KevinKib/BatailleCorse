// The entry point (/ and the old /games) is a page that offers every game; no game's menu
// advertises another one, and Back always leads to the picker.
import { GAMES } from '../../src/model/games';
import { expectGap } from '../support/spacing';

// Phones first: every game must fit without any scrolling, however many GAMES the app registers.
const VIEWPORTS: Array<[number, number]> = [[375, 667], [360, 640], [390, 844], [768, 1024], [1280, 720]];

describe('Game picker', () => {
  it('redirects / and unknown routes to /', () => {
    cy.visit('/');
    cy.location('pathname').should('eq', '/');
    cy.visit('/this/does/not/exist');
    cy.location('pathname').should('eq', '/');
  });

  it('sends an old /games/... invite link to the same page without the prefix', () => {
    cy.visit('/games/president/join/abc');
    cy.location('pathname').should('eq', '/president/join/abc');
  });

  it('shows the title and every game with description, player count and Play', () => {
    cy.visit('/');
    cy.get('[data-test="picker-title"]').should('be.visible').and('contain.text', 'Card Games');

    cy.get('[data-test="choice-bataillecorse"]').within(() => {
      cy.contains('h2', 'Bataille Corse');
      cy.contains('Fast two-player duel. Slap the pile when you spot a pattern.');
      cy.contains('2 players');
      cy.contains('button', 'Play');
    });
    cy.get('[data-test="choice-bullshit"]').within(() => {
      cy.contains('h2', 'Bullshit');
      cy.contains('Bluff your way to an empty hand. Call out the liars.');
      cy.contains('2–6 players');
      cy.contains('button', 'Play');
    });
    cy.get('[data-test="choice-kobo"]').within(() => {
      cy.contains('h2', 'Kobo');
      cy.contains('Remember your hidden cards and end the round with the fewest points.');
      cy.contains('2–6 players');
      cy.contains('button', 'Play');
    });
    cy.get('[data-test="choice-three-men-morris"]').within(() => {
      cy.contains('h2', 'Morris');
      cy.contains("Tapatan, Achi or Six Men's Morris. Place, then slide.");
      cy.contains('2 players');
      cy.contains('button', 'Play');
    });
    cy.get('[data-test="choice-president"]').within(() => {
      cy.contains('h2', 'President');
      cy.contains('Shed your cards first and take the top title.');
      cy.contains('3–8 players');
      cy.contains('button', 'Play');
    });
    cy.get('[data-test="choice-mangola"]').within(() => {
      cy.contains('h2', 'Mangola');
      cy.contains('Lay out your seeds, then sow and capture. Take every seed of your opponent.');
      cy.contains('2 players');
      cy.contains('button', 'Play');
    });
    cy.get('[data-test="choice-ninety-nine"]').within(() => {
      cy.contains('h2', '99');
      cy.contains('Keep the running total at 99 or less. Whoever cannot play loses a life.');
      cy.contains('2–12 players');
      cy.contains('button', 'Play');
    });
    cy.get('[data-test="choice-draughts"]').within(() => {
      cy.contains('h2', 'Draughts');
      cy.contains('International draughts on a 10 by 10 board. Take every piece, or leave your opponent without a move.');
      cy.contains('2 players');
      cy.contains('button', 'Play');
    });
    cy.get('[data-test="choice-blackjack"]').within(() => {
      cy.contains('h2', 'Blackjack');
      cy.contains('Beat the dealer, alone or in a 100-point tournament');
      cy.contains('1–6 players');
      cy.contains('button', 'Play');
    });
    cy.get('[data-test="choice-kilo-de-merde"]').within(() => {
      cy.contains('h2', 'Kilo de merde');
      cy.contains('Pass, collect four of a kind, slap the pile. Last one takes the kilos.');
      cy.contains('3–10 players');
      cy.contains('button', 'Play');
    });
  });

  it('plays President: Game home with New game and Join game, Back returns to the picker', () => {
    cy.visit('/');
    cy.get('[data-test="play-president"]').click();
    cy.location('pathname').should('eq', '/president');
    cy.contains('h1', 'President').should('be.visible');
    cy.contains('button', 'New game').should('be.visible');
    cy.contains('button', 'Join game').should('be.visible');
    cy.get('[data-test="back"]').click();
    cy.location('pathname').should('eq', '/');
  });

  it('plays Mangola: Game home with New game and Join game, Back returns to the picker', () => {
    cy.visit('/');
    cy.get('[data-test="play-mangola"]').click();
    cy.location('pathname').should('eq', '/mangola');
    cy.contains('h1', 'Mangola').should('be.visible');
    cy.contains('button', 'New game').should('be.visible');
    cy.contains('button', 'Join game').should('be.visible');
    cy.get('[data-test="back"]').click();
    cy.location('pathname').should('eq', '/');
  });

  it('plays 99: Game home with New game and Join game, Back returns to the picker', () => {
    cy.visit('/');
    cy.get('[data-test="play-ninety-nine"]').click();
    cy.location('pathname').should('eq', '/ninety-nine');
    cy.contains('h1', '99').should('be.visible');
    cy.contains('button', 'New game').should('be.visible');
    cy.contains('button', 'Join game').should('be.visible');
    cy.get('[data-test="back"]').click();
    cy.location('pathname').should('eq', '/');
  });

  it('plays Kilo de merde: Game home with New game and Join game, Back returns to the picker', () => {
    cy.visit('/');
    cy.get('[data-test="play-kilo-de-merde"]').click();
    cy.location('pathname').should('eq', '/kilo-de-merde');
    cy.contains('h1', 'Kilo de merde').should('be.visible');
    cy.contains('button', 'New game').should('be.visible');
    cy.contains('button', 'Join game').should('be.visible');
    cy.get('[data-test="back"]').click();
    cy.location('pathname').should('eq', '/');
  });

  it('plays Kilo de merde: Game home with New game and Join game, Back returns to the picker', () => {
    cy.visit('/');
    cy.get('[data-test="play-kilo-de-merde"]').click();
    cy.location('pathname').should('eq', '/kilo-de-merde');
    cy.contains('h1', 'Kilo de merde').should('be.visible');
    cy.contains('button', 'New game').should('be.visible');
    cy.contains('button', 'Join game').should('be.visible');
    cy.get('[data-test="back"]').click();
    cy.location('pathname').should('eq', '/');
  });

  it('plays Blackjack: Game home with New game and Join game, Back returns to the picker', () => {
    cy.visit('/');
    cy.get('[data-test="play-blackjack"]').click();
    cy.location('pathname').should('eq', '/blackjack');
    cy.contains('h1', 'Blackjack').should('be.visible');
    cy.contains('button', 'New game').should('be.visible');
    cy.contains('button', 'Join game').should('be.visible');
    cy.get('[data-test="back"]').click();
    cy.location('pathname').should('eq', '/');
  });

  it('plays Draughts: Game home with New game and Join game, Back returns to the picker', () => {
    cy.visit('/');
    cy.get('[data-test="play-draughts"]').click();
    cy.location('pathname').should('eq', '/draughts');
    cy.contains('h1', 'Draughts').should('be.visible');
    cy.contains('button', 'New game').should('be.visible');
    cy.contains('button', 'Join game').should('be.visible');
    cy.get('[data-test="back"]').click();
    cy.location('pathname').should('eq', '/');
  });

  it("plays Three Men's Morris: Game home with New game and Join game, Back returns to the picker", () => {
    cy.visit('/');
    cy.get('[data-test="play-three-men-morris"]').click();
    cy.location('pathname').should('eq', '/three-men-morris');
    cy.contains('button', 'New game').should('be.visible');
    cy.contains('button', 'Join game').should('be.visible');
    cy.get('[data-test="back"]').click();
    cy.location('pathname').should('eq', '/');
  });

  it('plays Bataille Corse: Game home without any Bullshit button, Back returns to the picker', () => {
    cy.visit('/');
    cy.get('[data-test="play-bataillecorse"]').click();
    cy.location('pathname').should('eq', '/bataillecorse');
    cy.contains('button', 'New game').should('be.visible');
    cy.contains('button', 'Join game').should('be.visible');
    cy.contains('Play Bullshit').should('not.exist');
    cy.get('[data-test="back"]').click();
    cy.location('pathname').should('eq', '/');
  });

  it('plays Bullshit: Game home with New game and Join game, Back returns to the picker', () => {
    cy.visit('/');
    cy.get('[data-test="play-bullshit"]').click();
    cy.location('pathname').should('eq', '/bullshit');
    cy.contains('button', 'New game').should('be.visible');
    cy.contains('button', 'Join game').should('be.visible');
    cy.get('[data-test="back"]').click();
    cy.location('pathname').should('eq', '/');
  });

  it('plays Kobo: Game home with New game and Join game, Back returns to the picker', () => {
    cy.visit('/');
    cy.get('[data-test="play-kobo"]').click();
    cy.location('pathname').should('eq', '/kobo');
    cy.contains('h1', 'Kobo').should('be.visible');
    cy.contains('button', 'New game').should('be.visible');
    cy.contains('button', 'Join game').should('be.visible');
    cy.get('[data-test="back"]').click();
    cy.location('pathname').should('eq', '/');
  });

  it('opens the privacy page from the footer link and goes back to the games', () => {
    cy.visit('/');
    cy.get('[data-test="privacy-link"]').click();
    cy.location('pathname').should('eq', '/privacy');
    cy.contains('h1', 'Privacy').should('be.visible');
    cy.get('[data-test="privacy-back"]').click();
    cy.location('pathname').should('eq', '/');
    cy.get('[data-test="picker-title"]').should('be.visible');
  });

  it('is operable with the keyboard (focus is visible on the Play buttons)', () => {
    cy.visit('/');
    cy.get('[data-test="play-bataillecorse"]').focus().should('have.focus');
    cy.get('[data-test="play-bataillecorse"]').then(($el) => {
      const style = getComputedStyle($el[0]);
      expect(style.outlineStyle !== 'none' || style.boxShadow !== 'none').to.eq(true);
    });
  });

  VIEWPORTS.forEach(([width, height]) => {
    it(`fits every game with no scrolling at ${width}x${height}`, () => {
      cy.viewport(width, height);
      cy.visit('/');
      cy.get('[data-test^="choice-"]').should('have.length', GAMES.length);
      GAMES.forEach(({ key }) => {
        cy.get(`[data-test="play-${key}"]`).should('be.visible').then(($el) => {
          const box = $el[0].getBoundingClientRect();
          expect(box.height, `${key} Play height`).to.be.at.least(44);
          expect(box.bottom, `${key} Play bottom`).to.be.at.most(height);
          expect(box.right, `${key} Play right`).to.be.at.most(width);
        });
        // Nothing is clipped: the title, pitch and player count of each card stay inside it.
        cy.get(`[data-test="choice-${key}"]`).then(($card) => {
          const card = $card[0];
          expect(card.scrollWidth, `${key} card overflow x`).to.be.at.most(card.clientWidth);
          expect(card.scrollHeight, `${key} card overflow y`).to.be.at.most(card.clientHeight);
          const outer = card.getBoundingClientRect();
          card.querySelectorAll('h2, p, span, button').forEach((el) => {
            const box = el.getBoundingClientRect();
            expect(box.left, `${key} ${el.tagName} left`).to.be.at.least(outer.left - 0.5);
            expect(box.right, `${key} ${el.tagName} right`).to.be.at.most(outer.right + 0.5);
          });
        });
      });
      // The Privacy link breathes: clear of the games, and 8 px or more above the card's bottom edge.
      expectGap('.choices', '[data-test="privacy-link"]', 8);
      cy.get('[data-test="privacy-link"]').then(($link) => {
        const edge = ($link[0].closest('.title-panel') as HTMLElement).getBoundingClientRect().bottom;
        expect(edge - $link[0].getBoundingClientRect().bottom + 0.5, 'Privacy link to the card bottom').to.be.at.least(8);
      });
      cy.window().then((win) => {
        const doc = win.document.documentElement;
        expect(doc.scrollHeight, 'page height').to.be.at.most(win.innerHeight);
        expect(doc.scrollWidth, 'page width').to.be.at.most(win.innerWidth);
        const screen = win.document.querySelector('.titlescreen') as HTMLElement;
        expect(screen.scrollHeight, 'picker screen height').to.be.at.most(screen.clientHeight);
        expect(screen.scrollWidth, 'picker screen width').to.be.at.most(screen.clientWidth);
      });
    });
  });
});
