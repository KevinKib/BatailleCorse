import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

// Everything under frontend/public is served as-is by the production site. Manual debug pages
// (WebSocket test pages loading scripts from a CDN) must not live there nor at the frontend root.
const frontendRoot = resolve(__dirname, '..');
const publicDir = join(frontendRoot, 'public');

function listFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) =>
    entry.isDirectory() ? listFiles(join(dir, entry.name)) : [join(dir, entry.name)],
  );
}

const thirdPartyScriptHosts = /cdn\.jsdelivr\.net|unpkg\.com|cdnjs\.cloudflare\.com|code\.jquery\.com|googleapis|gstatic/i;

describe('static assets', () => {
  it('serves no static HTML page from frontend/public', () => {
    const htmlFiles = listFiles(publicDir).filter((file) => /\.html?$/i.test(file));
    expect(htmlFiles).toEqual([]);
  });

  it('keeps manual debug pages out of the frontend root', () => {
    const rootHtml = readdirSync(frontendRoot).filter((name) => /\.html?$/i.test(name));
    expect(rootHtml).toEqual(['index.html']);
  });

  it('loads nothing from a third-party CDN or font host in public files or index.html', () => {
    const files = [...listFiles(publicDir), join(frontendRoot, 'index.html')];
    const offenders = files.filter((file) => thirdPartyScriptHosts.test(readFileSync(file, 'utf8')));
    expect(offenders).toEqual([]);
  });
});
