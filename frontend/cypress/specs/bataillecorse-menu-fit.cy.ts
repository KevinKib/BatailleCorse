// The Bataille Corse menu panels (home, create, join) must fit narrow phones:
// no horizontal scroll at 375 px, and the same content at every width.
const WIDTHS: Array<[number, number]> = [[375, 667], [1280, 720]];

const VIEWS: Array<{ name: string; path: string; texts: string[] }> = [
  { name: 'menu', path: '/games/bataillecorse', texts: ['Bataille Corse', 'New Game', 'Join Game', 'Back'] },
  { name: 'create', path: '/games/bataillecorse/create', texts: ['Create Game', 'Back to Lobby'] },
  { name: 'join', path: '/games/bataillecorse/join', texts: ['Join Game', 'Back to Lobby'] },
];

describe('Bataille Corse menu panels fit the viewport', () => {
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
