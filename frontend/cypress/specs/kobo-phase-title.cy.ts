// The phase title ("Alice is drawing") and the event line under it are centred on my zone, and the bar does not
// grow to do it. The browser is the guest in seat 1 and the scripted host plays, so the bar holds no button.
import { dealWithTopCard, startKoboTableAsGuest } from '../support/koboRoom';

const viewports = [
  { width: 375, height: 667, label: 'phone' },
  { width: 1280, height: 800, label: 'desktop' },
] as const;

const centreX = (el: Element) => {
  const r = el.getBoundingClientRect();
  return { c: (r.left + r.right) / 2, w: r.width, h: r.height };
};

describe('Kobo phase title', () => {
  viewports.forEach(({ width, height, label }) => {
    it(`is centred on my zone on a ${label}`, () => {
      cy.viewport(width, height);
      startKoboTableAsGuest(2, dealWithTopCard(2, '3S')).then(({ host, guests, mine }) => {
        cy.then(() => [host, mine, ...guests].forEach((p) => p.publish('/app/kobo/ready', {})));
        cy.get('[data-test="phase"]').invoke('text').should('match', / is drawing$/);
        cy.then(() => host.publish('/app/kobo/draw', {}));
        cy.get('[data-test="phase"]').invoke('text').should('match', / is (choosing|drawing)/);
        cy.document().then((doc) => {
          const zone = centreX(doc.querySelector('[data-test="my-zone"]')!);
          const bar = doc.querySelector('[data-test="kobo-actions"]')!;
          const phase = doc.querySelector('[data-test="phase"]')!;
          const log = doc.querySelector('[data-test="event-log-line"]')!;
          // Text centre, measured on the text itself (a block-level paragraph is as wide as its column).
          const textCentre = (el: Element) => {
            const range = doc.createRange();
            range.selectNodeContents(el);
            const r = range.getBoundingClientRect();
            return (r.left + r.right) / 2;
          };
          const barBox = centreX(bar);
          cy.log(`zone ${zone.c} phase ${textCentre(phase)} log ${textCentre(log)} bar height ${barBox.h}`);
          expect(Math.abs(textCentre(phase) - zone.c), 'phase title off-centre (px)').to.be.at.most(2);
          // The event line is empty until an event names a slot: only a line that is there is measured.
          if (log.textContent) expect(Math.abs(textCentre(log) - zone.c), 'event line off-centre (px)').to.be.at.most(2);
          // The bar keeps its reserved height (see kobo-stable-layout): it neither grows nor shrinks with the text.
          expect(barBox.h, 'bar height at rest').to.eq(label === 'phone' ? 64 : 52);
        });
      });
    });
  });
});
