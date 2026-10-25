import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

// The tab title names the site (CardGamesZone), not one of its games.
describe('index.html', () => {
  const html = readFileSync(resolve(__dirname, '../index.html'), 'utf8');

  it('givenTheStaticPage_thenTheTabTitleIsCardGamesZone', () => {
    expect(html).toMatch(/<title>\s*CardGamesZone\s*<\/title>/);
  });

  it('givenTheStaticPage_thenNoMetaTitleStillNamesBatailleCorse', () => {
    expect(html).not.toMatch(/Bataille\s*Corse/i);
  });
});
