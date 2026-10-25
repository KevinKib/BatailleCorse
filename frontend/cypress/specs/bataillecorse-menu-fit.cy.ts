// The Game home, create and join panels of both games must fit narrow phones:
// no horizontal scroll at 375 px, and the same content at every width.
const WIDTHS: Array<[number, number]> = [[375, 667], [1280, 720]];

const VIEWS: Array<{ name: string; path: string; texts: string[] }> = [
  { name: 'Bataille Corse home', path: '/bataillecorse', texts: ['Bataille Corse', 'New game', 'Join game', 'Back'] },
  { name: 'Bataille Corse join', path: '/bataillecorse/join', texts: ['Join game', 'Back'] },
  { name: 'Bullshit home', path: '/bullshit', texts: ['Bullshit', 'New game', 'Join game', 'Back'] },
  { name: 'Bullshit join', path: '/bullshit/join', texts: ['Game ID', 'Join game', 'Back'] },
];

describe('Game home, create and join panels fit the viewport', () => {
  for (const [w, h] of WIDTHS) {
    for (const view of VIEWS) {
      it(`${view.name} at ${w}px: no horizontal overflow, content preserved`, () => {
        cy.viewport(w, h);
        cy.visit(view.path);
        cy.get('.title-panel').should('be.visible');
        for (const text of view.texts) cy.contains(text).should('be.visible');
        cy.document().then((doc) => {
          const el = doc.scrollingElement as Element;
          expect(el.scrollWidth, 'page scrollWidth').to.be.at.most(el.clientWidth);
        });
        cy.get('.title-panel').then(($p) => {
          const r = $p[0].getBoundingClientRect();
          expect(r.left, 'panel left edge').to.be.at.least(0);
          expect(r.right, 'panel right edge').to.be.at.most(w);
          if (w <= 480) {
            expect(r.left, 'gutter left').to.be.at.least(8);
            expect(w - r.right, 'gutter right').to.be.at.least(8);
          }
          // Nothing inside the panel spills out of it (the decorative card fan
          // is aria-hidden and intentionally sits above the panel).
          $p[0].querySelectorAll('*').forEach((c) => {
            const cr = c.getBoundingClientRect();
            if (cr.width === 0 || c.closest('[aria-hidden="true"]')) return;
            expect(cr.right, `${c.tagName}.${c.className} right`).to.be.at.most(r.right + 0.5);
            expect(cr.left, `${c.tagName}.${c.className} left`).to.be.at.least(r.left - 0.5);
          });
        });
      });
    }
  }
});
