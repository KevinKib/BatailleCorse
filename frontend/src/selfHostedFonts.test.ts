// @vitest-environment node
import { describe, it, expect, beforeAll } from 'vitest';
import { readFileSync, readdirSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { build } from 'vite';

const frontendRoot = resolve(__dirname, '..');
const thirdPartyFontHosts = /googleapis|gstatic/i;

describe('self-hosted fonts', () => {
  it('givenIndexHtml_thenItNamesNoGoogleFontHost', () => {
    const html = readFileSync(join(frontendRoot, 'index.html'), 'utf8');
    expect(html).not.toMatch(thirdPartyFontHosts);
  });

  it('givenMainEntry_thenItImportsTheFontFaceStylesheet', () => {
    const main = readFileSync(join(frontendRoot, 'src/main.ts'), 'utf8');
    expect(main).toContain("import './fonts.css'");
  });

  it('givenTheFontFaceStylesheet_thenItDeclaresOnlyLocalWoff2FilesWithSwap', () => {
    const css = readFileSync(join(frontendRoot, 'src/fonts.css'), 'utf8');
    const faces = css.match(/@font-face\s*\{[^}]*\}/g) ?? [];
    expect(faces.length).toBe(8); // 400 and 700, upright and italic, Latin and Latin Extended
    for (const face of faces) {
      expect(face).toContain('font-display: swap');
      expect(face).toMatch(/url\('\.\.\/node_modules\/@fontsource\/atkinson-hyperlegible\/files\/[^')]+\.woff2'\) format\('woff2'\)/);
      expect(face).not.toMatch(/https?:|\.woff[^2]/);
    }
  });

  describe('built output', () => {
    let outDir = '';
    let assets: string[] = [];

    beforeAll(async () => {
      outDir = mkdtempSync(join(tmpdir(), 'bc-fonts-'));
      await build({ root: frontendRoot, logLevel: 'silent', build: { outDir, emptyOutDir: true } });
      assets = readdirSync(join(outDir, 'assets'));
      return () => rmSync(outDir, { recursive: true, force: true });
    }, 180_000);

    it('givenTheBuild_thenNoEmittedFileNamesAGoogleFontHost', () => {
      const textFiles = ['index.html', ...assets.filter((f) => /\.(css|js)$/.test(f)).map((f) => join('assets', f))];
      for (const file of textFiles) {
        expect(readFileSync(join(outDir, file), 'utf8'), file).not.toMatch(thirdPartyFontHosts);
      }
    });

    it('givenTheBuild_thenTheFontFacesPointToBundledWoff2Files', () => {
      const css = assets
        .filter((f) => f.endsWith('.css'))
        .map((f) => readFileSync(join(outDir, 'assets', f), 'utf8'))
        .join('\n');
      expect(css).toMatch(/@font-face\{[^}]*font-family:Atkinson Hyperlegible/);
      expect(css).toMatch(/font-display:swap/);
      expect(css).not.toMatch(/url\(https?:/);
      expect(assets.some((f) => /atkinson-hyperlegible.*\.woff2$/.test(f))).toBe(true);
    });
  });
});
