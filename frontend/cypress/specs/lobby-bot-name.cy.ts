// The shared lobby seat row keeps a bot's name readable ("Bot 1", not "Bo…") next to the bot badge, the level
// select and the remove cross, at phone width and on desktop, in every game that offers bots.
const SIZES: Array<[number, number]> = [[375, 667], [1280, 800]];

const GAMES: Array<{ label: string; path: string; room: RegExp; option: string | null }> = [
  { label: "Three Men's Morris", path: '/three-men-morris', room: /\/room\//, option: 'level' },
  { label: 'Bataille Corse', path: '/bataillecorse', room: /\/room\//, option: 'tier' },
  { label: 'Bullshit', path: '/bullshit', room: /\/room\//, option: null },
  { label: 'President', path: '/president', room: /\/room\//, option: 'level' },
];

function lobbyWithBot(path: string) {
  cy.visit(path);
  cy.get('[data-test="name"]').type('Alice');
  cy.get('[data-test="new-game"]').click();
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  cy.get('[data-test="add-bot"]').click();
  cy.get('[data-test="badge-bot"]').should('be.visible');
}

describe('Lobby seat row with a bot', () => {
  GAMES.forEach(({ label, path, option }) => {
    SIZES.forEach(([width, height]) => {
      it(`${label}: keeps the bot name readable and every control at ${width}x${height}`, () => {
        cy.viewport(width, height);
        lobbyWithBot(path);
        // Documentation shots: `--env shots=before` (or `after`) saves the lobby as <shots>-<game>-<size>.png.
        const shots = Cypress.env('shots');
        if (shots) cy.screenshot(`${shots}-${path.split('/').pop()}-${width}x${height}`, { capture: 'viewport' });
        const row = () => cy.get('.players li').eq(1);

        // The name is shown in full, not cut by an ellipsis.
        row().find('.player-name').should('have.text', 'Bot 1').then(($name) => {
          const el = $name[0];
          expect(el.scrollWidth, 'name not truncated').to.be.at.most(el.clientWidth);
          // Room for about six characters of the 16px bold name (plus its 14px of padding and border), so a longer
          // label or a wider font still shows more than "Bo…".
          expect(el.getBoundingClientRect().width, 'name width').to.be.at.least(66);
        });

        // Everything the row shows today is still there, and the touch targets keep 44 px.
        row().find('.player-seat').should('have.text', '2').and('be.visible');
        row().find('[data-test="badge-bot"]').should('be.visible');
        cy.get('[data-test="remove-bot-1"]').should('be.visible').then(($b) => {
          const r = $b[0].getBoundingClientRect();
          expect(r.width).to.be.at.least(44);
          expect(r.height).to.be.at.least(44);
        });
        if (option) {
          cy.get(`[data-test="seat-option-1-${option}"]`).should('be.visible').then(($s) => {
            expect($s[0].getBoundingClientRect().height).to.be.at.least(44);
          });
        }
        // No control leaves the row's box, and the page does not scroll sideways.
        row().then(($row) => {
          const box = $row[0].getBoundingClientRect();
          $row.find('.player-seat, .chip, select, button').each((_, el) => {
            const r = el.getBoundingClientRect();
            expect(r.left, 'left in row').to.be.at.least(box.left - 1);
            expect(r.right, 'right in row').to.be.at.most(box.right + 1);
          });
        });
        cy.document().then((doc) => {
          expect(doc.documentElement.scrollWidth).to.be.at.most(doc.documentElement.clientWidth);
        });
      });
    });
  });
});
