// The entry point (/ and /games) is a page that offers both games; neither game's menu
// advertises the other one, and Back always leads to the picker.
const VIEWPORTS: Array<[number, number]> = [[375, 667], [1280, 720]];

describe('Game picker', () => {
  it('redirects / and unknown routes to /games', () => {
    cy.visit('/');
    cy.location('pathname').should('eq', '/games');
    cy.visit('/this/does/not/exist');
    cy.location('pathname').should('eq', '/games');
  });

  it('shows the title and both games with description, player count and Play', () => {
    cy.visit('/games');
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
  });

  it('plays Bataille Corse: menu without any Bullshit button, Back returns to the picker', () => {
    cy.visit('/games');
    cy.get('[data-test="play-bataillecorse"]').click();
    cy.location('pathname').should('eq', '/games/bataillecorse');
    cy.contains('button', 'New Game').should('be.visible');
    cy.contains('button', 'Join Game').should('be.visible');
    cy.contains('Play Bullshit').should('not.exist');
    cy.get('[data-test="back"]').click();
    cy.location('pathname').should('eq', '/games');
  });

  it('plays Bullshit: create screen, Back returns to the picker', () => {
    cy.visit('/games');
    cy.get('[data-test="play-bullshit"]').click();
    cy.location('pathname').should('eq', '/games/bullshit/create');
    cy.get('[data-test="back"]').click();
    cy.location('pathname').should('eq', '/games');
  });

  it('is operable with the keyboard (focus is visible on the Play buttons)', () => {
    cy.visit('/games');
    cy.get('[data-test="play-bataillecorse"]').focus().should('have.focus');
    cy.get('[data-test="play-bataillecorse"]').then(($el) => {
      const style = getComputedStyle($el[0]);
      expect(style.outlineStyle !== 'none' || style.boxShadow !== 'none').to.eq(true);
    });
  });

  VIEWPORTS.forEach(([width, height]) => {
    it(`needs no page scroll and shows both Play buttons at ${width}x${height}`, () => {
      cy.viewport(width, height);
      cy.visit('/games');
      cy.get('[data-test="play-bataillecorse"]').should('be.visible');
      cy.get('[data-test="play-bullshit"]').should('be.visible');
      cy.window().then((win) => {
        const doc = win.document.documentElement;
        expect(doc.scrollHeight, 'page height').to.be.at.most(win.innerHeight);
        expect(doc.scrollWidth, 'page width').to.be.at.most(win.innerWidth);
        const screen = win.document.querySelector('.titlescreen') as HTMLElement;
        expect(screen.scrollHeight, 'picker screen height').to.be.at.most(screen.clientHeight);
      });
      cy.get('[data-test="play-bullshit"]').then(($el) => {
        expect($el[0].getBoundingClientRect().height).to.be.at.least(44);
      });
    });
  });
});
