const OUT = 'C:/Users/kevin/AppData/Local/Temp/claude/C--Users-kevin-Documents-Git-BatailleCorse/f0441d98-b3c7-495f-aa14-9dce1716ce0e/scratchpad/' + (Cypress.env('tag') || 'snap');
function dump(doc: Document, label: string) {
  const el = doc.scrollingElement as Element;
  const cards = [...doc.querySelectorAll('article')].map((a) => {
    const r = a.getBoundingClientRect();
    const b = a.querySelector('button')!.getBoundingClientRect();
    return { key: a.getAttribute('data-test'), text: (a as HTMLElement).innerText.replace(/\s+/g, ' ').trim(), aria: a.querySelector('button')!.getAttribute('aria-label'), box: [r.left, r.top, r.width, r.height].map(Math.round), btn: [Math.round(b.width), Math.round(b.height)] };
  });
  return { label, h1: doc.querySelector('h1')!.textContent, title: doc.title, scrollW: el.scrollWidth, clientW: el.clientWidth, scrollH: el.scrollHeight, clientH: el.clientHeight, cards };
}
describe('snapshot', () => {
  it('picker', () => {
    const out: any[] = [];
    for (const [w, h] of [[375, 667], [1280, 800]]) {
      cy.viewport(w, h);
      cy.visit('/games');
      cy.get('article').should('have.length.at.least', 2);
      cy.document().then((d) => out.push(dump(d, w + 'x' + h)));
    }
    cy.then(() => cy.writeFile(OUT + '.json', out));
  });
});
