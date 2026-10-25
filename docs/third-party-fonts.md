# Third-party fonts

The site serves its own web font. The browser makes no request to Google Fonts or any other font host.

| Font | Source | Version | Licence |
| --- | --- | --- | --- |
| Atkinson Hyperlegible (400 and 700, upright and italic) | npm package `@fontsource/atkinson-hyperlegible` | 5.3.0 (pinned) | SIL Open Font License 1.1 |

Atkinson Hyperlegible is by the Braille Institute of America, Inc. The licence text ships with the package
(`frontend/node_modules/@fontsource/atkinson-hyperlegible/LICENSE`); the font is used unmodified.

## How it is wired

- `frontend/src/fonts.css` declares eight `@font-face` rules (weights 400 and 700, upright and italic, Latin and
  Latin Extended subsets), woff2 only, `font-display: swap`. Each rule points at a file of the npm package, so
  Vite bundles the files into `dist/assets` with a content hash. The browser downloads a file only when a
  character of its `unicode-range` is drawn (in practice the two upright Latin files).
- `frontend/src/main.ts` imports `fonts.css`. The family name and the CSS variables `--font-title` and
  `--font-ui` (`App.vue`) did not change.
- `frontend/nginx.conf` caches `/assets/` for a year (`immutable`): hashed file names make that safe.
- `frontend/src/selfHostedFonts.test.ts` fails if `index.html` or any built file names `googleapis` or `gstatic`.

## Upgrading or changing the font

Bump the exact version of `@fontsource/atkinson-hyperlegible` in `frontend/package.json`, regenerate the lock
file with `npm run lock:sync`, and check that the file names in `fonts.css` still exist in the package.
