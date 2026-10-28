# French translation of the whole UI: plan

Spec: `docs/specs/2026-10-28-french-translation-design.md`. **Produced by Opus 5.5** on 2026-10-28 from
`origin/main` at `b787ecb8`.

## Key decisions (from the spec)

1. **Locale resolved once at boot, no URL prefix** (D1): `bc_locale` in `localStorage`, else
   `navigator.languages`, else English; `<html lang>` follows; switching writes the choice and reloads, so the 57
   `useI18n()` call sites stay as they are.
2. **Backend stays language-neutral** (D3): it already sends codes everywhere except the default "Player N" / "Bot N"
   names, which become `null` and are named by the frontend. Server `message` strings stay English diagnostics,
   never rendered.
3. **Locale-aware `plural`, `ordinal` and `formatList` on `Intl`** (D2), no `vue-i18n`.
4. **Runtime parity tests** (D4): no type check runs in CI, so Vitest checks that French has exactly the English
   shape and placeholders, French typography, and that no template holds hard-coded text.
5. **French split per area in `locales/fr/`, lazy-loaded and dark-launched** (D6) until the integration chunk;
   an agent drafts from a glossary the owner settles first (D9).

**Owner gates before chunks start:** the glossary, the register (vous/tu) and the other questions of the spec
(gate G, before T1 to T5); a Claude Design canvas for the language picker (gate D, before L). P0, I1, H and B do not
wait for them.

**Tests (every PR):** `cd backend && mvn -s ../deploy/docker/settings.xml test`; `cd frontend && npm test && npx
vite build`. Cypress only in X (issue #312 rule: only the specs named there, once; a failing one rerun alone at most
once), on a shifted-port e2e stack with its own compose project name. Dev environment only for L and X (visible
change), on shifted ports.

**Conventions:** branches `feat/i18n-...`; a draft PR with its real number at the start of each chunk; test-first
visible in git (`test:` commit before `feat:`); frontend paths relative to `frontend/src/`, backend paths relative
to `backend/src/main/java/org/kevinkib/cardgames/`.

## Status

| Chunk | Title | Cost | Needs | Runs |
|-------|-------|------|-------|------|
| P0 | `feat: i18n contract for a second locale` | S (< 30 min) | this plan merged | first, alone |
| I1 | `feat: locale detection, locale-aware plurals and lazy French bundle` | M | P0 | parallel |
| H | `feat: move hard-coded UI text to the locales` | S | P0 | parallel |
| B | `feat: send unnamed seats without a default English name` | M | P0 (none in fact: backend + one frontend file) | parallel |
| L | `feat: language picker` | S | P0, gate D | parallel (needs I1 merged only to try it for real) |
| T1 | `feat: French texts for the shell, lobby and Bataille Corse` | M | P0, gate G | parallel |
| T2 | `feat: French texts for Bullshit and President` | L | P0, gate G | parallel |
| T3 | `feat: French texts for Kobo` | M | P0, gate G | parallel |
| T4 | `feat: French texts for Morris and Mangola` | L | P0, gate G | parallel |
| T5 | `feat: French privacy page` | S | I1 merged (it adds `bc_locale` to the English text), gate G | parallel once I1 is in |
| X | `feat: French available to players` | M | all above merged | last (integration) |

Parallelism: after P0, I1, H, B, L, T1, T2, T3 and T4 run together, each in its own worktree with one subagent; T5
joins when I1 is merged; X runs alone at the end.

## Files at risk of conflict

| File | Chunks | Handling |
|------|--------|----------|
| `locales/Messages.ts`, `locales/en.ts` | P0 (every new key: hard-coded texts, list words, language picker), I1 (`privacy` storage paragraph only) | no other chunk adds keys; a chunk that finds a missing key adds it at the end of its area and says so in its PR |
| `locales/en.test.ts` | P0 only | |
| `locales/fr/index.ts` | P0 only | translation chunks edit only their own area files |
| `locales/fr/<area>.ts` | one translation chunk each (see T1 to T5) | |
| `locales/format.ts`, `format.test.ts` | P0 (`formatList` signature, English behaviour), I1 (locale-aware behaviour) | series P0 then I1 |
| `locales/index.ts`, `composables/useI18n.ts`, `main.ts`, `index.html` | I1 only | |
| `view/president/PresidentGameScreen.vue` | H only | |
| `view/alpha/GameScreen.vue` | H only | |
| `components/PublicGameList.vue` | B only | |
| `cypress/support/*` | X only | |
| `docs/specs/2026-10-28-french-translation-design.md`, `CLAUDE.md`, `README.md` | X only | |

## P0 `feat: i18n contract for a second locale` (S, under 30 minutes)

Shared interfaces, no visible change, nothing deleted.

- `locales/index.ts`: `export const SUPPORTED_LOCALES = ['en'] as const` plus a wider `Locale = 'en' | 'fr'`; keep
  `messages` and `DEFAULT_LOCALE`.
- `locales/locale.ts` (new): signatures with an English-only body: `resolveLocale(stored: string | null,
  browser: readonly string[]): Locale` (returns `'en'`), `currentLocale(): Locale`, `initLocale(): Promise<void>`
  (no-op), `setLocale(locale: Locale): void` (no-op), `LOCALE_STORAGE_KEY = 'bc_locale'`.
- `locales/format.ts`: `formatList(items: readonly string[], type: 'conjunction' | 'disjunction'): string`
  using `Intl.ListFormat('en')` for now, with a test.
- `Messages.ts` + `en.ts` + `en.test.ts`, new keys (English values identical to today's text):
  `batailleCorseUi.back`, `.send`, `.slap`, `.you`; `gamePicker.playAria` ('{play} {name}');
  `mangolaUi.move.sentence` ('{head}: {details}.') and `.sentenceAlone` ('{head}.'); `language: { label,
  names: { en: 'English', fr: 'Français' } }`.
- `locales/fr/`: `index.ts` composing `messagesFr: Messages` from area files that re-export the English area for
  now (`export const presidentUi: Messages['presidentUi'] = messagesEn.presidentUi;`). Area files: `shell.ts`
  (T1 areas), `cards.ts` (T2), `kobo.ts` (T3), `boards.ts` (T4), `privacy.ts` (T5); one file per chunk.
- `locales/parity.test.ts` (new): the D4 parity and placeholder checks between `messagesEn` and `messagesFr`
  (green: French is English for now), and the completeness check driven by a `TRANSLATED_AREAS` list, empty in P0.

## I1 `feat: locale detection, locale-aware plurals and lazy French bundle` (M)

TDD on the P0 signatures.

- `locale.ts`: real `resolveLocale` (stored value, then `navigator.languages` primary subtag, then `en`; only
  `SUPPORTED_LOCALES`), `initLocale` (resolve, load, set `document.documentElement.lang`, English on a failed
  load), `setLocale` (write `bc_locale`, `location.reload()`); `localStorage` access wrapped in `try/catch`.
- `locales/index.ts`: `loadMessages(locale)`: English eager, French by `import('./fr')`.
- `useI18n.ts`: returns the messages of `currentLocale()`; English before `initLocale()`.
- `format.ts`: `plural`, `ordinal`, `formatList` select with `Intl` for `currentLocale()`; tests in `en` and `fr`
  (French 0 singular, "1er"/"2e", "a, b et c", "1, 2 ou 3").
- `main.ts`: `initLocale().then(() => app.mount('#app'))`; `index.html` keeps `lang="en"` as the pre-boot value.
- `en.ts` `privacy` "What your browser stores": add the language choice `bc_locale`.
- Keep `SUPPORTED_LOCALES = ['en']` (dark launch); a test with `'fr'` injected proves the French path.

## H `feat: move hard-coded UI text to the locales` (S)

- `view/alpha/GameScreen.vue`: Back, Send, Slap and the `'You'` fallback use the P0 keys.
- `components/GameChoiceCard.vue`: aria-label through `gamePicker.playAria`.
- `model/president/hint.ts`: `listOr` becomes `formatList(..., 'disjunction')`.
- `model/mangola/announce.ts`: the sentence through `mangolaUi.move.sentence` / `.sentenceAlone` and
  `formatList(..., 'conjunction')` for the details.
- `view/kobo/KoboGameScreen.vue`, `view/president/PresidentGameScreen.vue`: winners through `formatList`;
  `PresidentGameScreen` drops its `Intl.ListFormat('en')`.
- New `locales/noHardCodedText.test.ts` (D4 point 5), with the allowlist kept short and commented.
- English output identical: existing tests unchanged, except the assembled list "a, b and c" where the current
  text was "a, b" (Kobo and President winners: confirm the new wording in the PR).

## B `feat: send unnamed seats without a default English name` (M, backend sessionmanagement)

- `sessionmanagement/core/domain/SessionPlayer.java`: store only the typed name; `name()` empty when none;
  remove `defaultName()`; `claimAsBot` keeps a typed bot name only.
- `SessionGame.java`: remove `botName()` and `renumberDefaultHumanNames()`; adapt `rename` (blank = no name).
- `PublicLobbies.java`: `hostName` null when the host typed none.
- DTOs (`presentation/dto/SessionViewDto.java`, the game DTOs listed by `grep -rl "\.name()" --include=*Dto*.java`):
  `null` for no name; Javadoc of `ErrorResponse`: `message` is a diagnostic, never shown to players.
- Frontend: `components/PublicGameList.vue` names a null host with the `playerLabel` of seat 1; check with a grep
  that every seat label goes through `seatDisplayName` or `seatLabel` (both already handle null).
- Tests first: domain tests for unnamed seats, a DTO test with `name: null`, `PublicLobbies` test, Vitest for the
  public list fallback. Existing Cypress `bullshit-player-names.cy.ts` ("Player 3") stays valid: the frontend
  produces the same text.

## L `feat: language picker` (S, after gate D)

- Build the option the owner chose on the canvas; uses `setLocale`, `language.*` keys, labels in each language's
  own name, `lang` attribute on each option.
- Component test (renders both choices, marks the current one, calls `setLocale`). Visual check with the
  `ui-change` skill at 375 px and 1280 px on the screens it lives on.

## T1 to T5: French texts (after gate G)

Each chunk replaces the English re-exports of its own file with French, following the settled glossary and
register, adds its areas to `TRANSLATED_AREAS` (completeness check) and passes the typography check on them.
The PR lists any glossary term it had to add.

| Chunk | File | Areas | Words |
|-------|------|-------|-------|
| T1 | `locales/fr/shell.ts` | `rules` (Bataille Corse), `batailleCorseUi`, `gamePicker`, `home`, `join`, `lobby`, `options`, `endScreen`, `endGame`, `disconnect`, `leaveGuard`, `hotkeys`, `turn`, `language` | about 800 |
| T2 | `locales/fr/cards.ts` | `bullshit`, `bullshitUi`, `president`, `presidentUi` | about 1,500 |
| T3 | `locales/fr/kobo.ts` | `kobo`, `koboUi` | about 900 |
| T4 | `locales/fr/boards.ts` | `threeMenMorris`, `threeMenMorrisUi`, `morrisLobby`, `morrisVariantRules`, `morrisBoard`, `mangola`, `mangolaUi` | about 1,500 |
| T5 | `locales/fr/privacy.ts` | `privacy` (with the I1 sentence) | about 600; owner reviews word by word |

## X `feat: French available to players` (M, integration)

- Merge state check; `SUPPORTED_LOCALES = ['en', 'fr']`; completeness check on every area.
- `cypress/support`: set `bc_locale = 'en'` before each visit. New `cypress/specs/i18n-french.cy.ts`: French
  browser gets French and `lang="fr"`; the picker switches to English; the choice survives a reload. Run only this
  spec plus one existing spec per game as a smoke check (issue #312 rule).
- `ui-change` pass in French at 375 px and 1280 px: picker, home, lobby, each game screen, end overlay, rules
  panel, privacy page; fix overflows (CSS or shorter French), list every change in the PR.
- Docs: project `CLAUDE.md` ("visible text in English and French"), `README.md` (how to add a key), spec status
  line, `docs/harnesses.md` if the harness changes.
- Comment on the roadmap issue #1 once merged.
