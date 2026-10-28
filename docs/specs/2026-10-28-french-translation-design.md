# French translation of the whole UI: design

**Produced by Opus 5.5** on 2026-10-28, from `origin/main` at `b787ecb8`. Architectural request (owner: "j'aimerais
implémenter une traduction française bientôt"). Plan: `docs/plans/2026-10-28-french-translation-plan.md`.

## Goal

Every text a player sees or hears (screen-reader labels and live announcements included) exists in French and in
English. A French-speaking browser gets French on its first visit, anyone can switch language, the choice is
remembered, and adding a key in one language without the other fails the tests. The backend stays
language-neutral.

Out of scope: a third language, translated URLs, server-side rendering or SEO per language, translating the
docs, the README or the backend logs.

## Current state (verified on `b787ecb8`)

- `frontend/src/locales/`: `Messages.ts` (the type, 839 lines), `en.ts` (the only bundle, 1,267 lines),
  `format.ts` (`format()`, `plural()`, `ordinal()`), `gameChoice.ts`, `index.ts` (`messages = { en }`,
  `DEFAULT_LOCALE = 'en'`), `en.test.ts` (562 lines of shape checks on English only).
- `composables/useI18n.ts` is the single seam: `useI18n(locale = DEFAULT_LOCALE)` returns a plain `Messages`
  object. It is called by **57 non-test files**, almost always once at setup (`const ui = useI18n().koboUi`): the
  texts are captured, not reactive.
- `plural()` picks `one` only for `n === 1` (French needs `one` for 0 and 1). `ordinal()` uses
  `Intl.PluralRules('en', { type: 'ordinal' })`. `PresidentGameScreen.vue` builds lists with
  `Intl.ListFormat('en')`.
- `index.html`: `<html lang="en">`, `<title>CardGamesZone</title>`, no meta description.
- No `vue-tsc` in the project nor in CI (`.github/workflows/ci.yml` says so): **the `Messages` type does not stop a
  build with a missing or extra key**. Only Vitest checks run in CI.
- PrimeVue: only `Button` and `InputText` are used; neither carries built-in text, so no PrimeVue locale is needed.
- Tests: 204 Vitest files, 89 Cypress specs (14 of them assert English text with `contains`). Vitest runs in
  happy-dom, Cypress in Electron, both with an `en-US` browser language.

## Inventory of user-visible text sources

| Source | Count | Where | Status for French |
|--------|-------|-------|-------------------|
| Locale keys (leaf strings) | **1,021** strings, 5,262 words, 27,782 characters; 35 plural pairs, 1 ordinal set, 350 placeholders | `locales/en.ts`, 27 top-level areas | translate (agent draft, owner review) |
| of which game rules and help panels | 302 strings (rules 25, bullshit 34, kobo 31, president 36, threeMenMorris 21, morrisVariantRules 52, mangola 30, morrisLobby 15...) | `en.ts` `rules`, `bullshit`, `kobo`, `president`, `threeMenMorris`, `morrisVariantRules`, `mangola` | translate; longest prose |
| of which privacy page | 28 strings, 580 words | `en.ts` `privacy` | translate; owner reviews word by word |
| of which bot names and levels | `botLabel` 'Bot {n}' (x4), `botWithLevel` '{name} ({level})' (x2), `botWithTier`, `join.botName`; levels: Bataille Corse tiers (9), Easy/Medium/Hard (x2), Easy/Medium/Expert | `en.ts` `*Ui`, `options` | already localisable; translate |
| Hard-coded text in components | **4** | `view/alpha/GameScreen.vue`: `label="Back"` (l. 80), `label="Send"` (l. 102), `label="Slap"` (l. 104), `'You'` fallback of `myLabel` (l. 255) | move to locale keys |
| Sentences assembled in code | **6** | `components/GameChoiceCard.vue:31` (`` `${play} ${name}` `` aria-label: word order); `model/president/hint.ts:9` (`' or '`, `', '`); `model/mangola/announce.ts:15` (`': '`, `'.'`); `view/kobo/KoboGameScreen.vue:172` and `view/president/PresidentGameScreen.vue:617` (winners `join(', ')`); `PresidentGameScreen.vue:540` (`Intl.ListFormat('en')`) | templates or locale-aware list formatting |
| Locale-bound formatting | **3** | `locales/format.ts` (`plural` rule, ordinal `'en'`); `index.html` `lang="en"` | make locale-aware |
| Comma lists for screen readers | 2 | `components/mangola/MangolaPit.vue:75`, `components/morris/MorrisBoard.vue:107` (`words.join(', ')`) | fine in French; keep |
| Backend default names | **2 rules** | `sessionmanagement/core/domain/SessionPlayer.defaultName()` = `"Player " + (seat + 1)`, stored as the name of a human who typed none, renumbered by `SessionGame.renumberDefaultHumanNames()`, sent in `SessionViewDto` and every game DTO, and as `hostName` by `PublicLobbies` (l. 65); `SessionGame.botName()` = `"Bot " + n` | **leaks English today**: the frontend shows a non-empty name as is. Send `null` instead (decision D3) |
| Backend event `message` strings | 70 literals ("Player 1 slapped and won.", "Game started."...) | `bataillecorse/presentation/*`, lifecycle broadcasters | **not rendered** by any frontend view; diagnostic only |
| Backend exception messages | ~240 | domain and presentation exceptions, sent as `ErrorResponse.message` | **not rendered verbatim**: `GameLobby` shows `lobby.actionFailed`, joins map HTTP status and `reason` (`joinErrorMessage.ts`), Mangola already sends the key `mangola.errors.stuckStart` |
| Backend option labels | all | option descriptors carry `labelKey` (`options.bataillecorse.tier`...) | already codes; translate the keys |
| Document title | 1 | `index.html` `CardGamesZone` | brand name: keep (owner question Q8) |

The Kobo event log, the trick and exchange texts of President, the Mangola move announcements and the Morris
announcements are already built in the frontend from codes and parameters: no backend sentence reaches the
screen.

## Design decisions

### D1. Locale selection: resolved once at boot, no URL prefix

Recommendation:

1. Order: the stored choice (`localStorage` key `bc_locale`, same `bc_` prefix as `bc_playerName`) if it names a
   supported locale, else the first entry of `navigator.languages` whose primary subtag is supported (`fr-CA`,
   `fr-BE` give `fr`), else `en`.
2. `main.ts` resolves the locale, loads its bundle, sets `document.documentElement.lang`, then mounts the app
   (`initLocale().then(() => app.mount('#app'))`: Vite 5's default build target does not allow a top-level
   `await`).
3. No language in the URL. Invite links and room links stay language-neutral: the person who opens a link gets
   their own language.
4. Switching language writes `bc_locale` and reloads the page (`location.reload()`). The 57 call sites keep
   capturing their texts once at setup, and the seat token in `localStorage` makes a reload inside a game a plain
   reconnection (well within the disconnect grace period). A reactive locale would mean turning every
   `const ui = useI18n().x` into a computed: churn on every screen and conflicts with every open branch, for a
   choice made once. Rejected.
5. Language picker: placement and look are an owner decision through a Claude Design canvas (Q3) before its chunk
   starts. Constraint for the canvas: reachable from the game picker without starting a game; labels are the
   language's own name ("English", "Français"), never flags.

`useI18n()` keeps its signature; it reads the current locale set by `initLocale()` instead of a constant. Before
`initLocale()` (unit tests) it returns English.

### D2. Plural, ordinal and list rules per locale, no new dependency

- `plural(forms, n, vars)` selects with `Intl.PluralRules(locale).select(n)`: `one` maps to `forms.one`, every
  other category to `forms.other`. English is unchanged; French gets the singular for 0 and 1 ("0 carte").
  `PluralForms` keeps its two fields: French and English need no other form for card or seed counts.
- `ordinal(n, forms)` selects with `Intl.PluralRules(locale, { type: 'ordinal' })`. `OrdinalForms` keeps its four
  fields; French fills `one: '{n}er'`, `two`/`few`/`other: '{n}e'` (French only ever yields `one` and `other`).
  The ordinals are places in President standings: masculine "1er" (owner can choose "1re" for "place", Q2).
- A new `formatList(items, type: 'conjunction' | 'disjunction')` in `format.ts` wraps
  `Intl.ListFormat(locale)` ("a, b et c", "1, 2 ou 3"); it replaces the hand-made lists of the inventory.
- No `vue-i18n`: the typed `Messages` tree, `format()` and these helpers already cover every need; a library would
  rewrite 57 call sites and lose the typed tree.

### D3. Backend-originated text: codes and parameters only

The backend already sends codes for everything visible except the default names. Recommendation:

- A seat stores only the name its player typed (empty when none). Every DTO sends `name: null` for a human who
  typed no name and for every bot; `PublicLobbies` sends `hostName: null`. `defaultName()`, `botName()` and the
  renumbering of default names go away (the seat number is the identity). The frontend already names a seat when
  `name` is empty (`model/table/displayName.ts`, `seatLabel` in `alpha/GameScreen.vue`: "Player N", "Bot N",
  "Bot N (level)"); `PublicGameList.vue` gains the same fallback for the host.
- Player-entered names are user content: never translated, always rendered as text (current rule kept).
- Event `message` strings and exception messages stay English diagnostics. Invariant written in the spec and in
  the `ErrorResponse` Javadoc: the frontend never renders a server `message`; a refusal the player must read gets
  a code (as `mangola.errors.stuckStart` and the join `reason` do) and a locale key.
- No `Accept-Language` handling on the server.

### D4. Test strategy (runtime, because CI has no type check)

1. **Parity**: a Vitest walks `messagesEn` and `messagesFr` together: same keys at every level, same array lengths,
   same `kind` per rules section, same keys in every `Record` (ranks, suits, option values).
2. **Placeholders**: at every leaf the set of `{name}` placeholders is identical between locales (plural forms
   compared per form).
3. **Completeness**: no French leaf equals its English leaf, except an explicit allowlist (proper nouns such as
   "Kobo", "Mangola", "Tapatan", "Achi"; numbers; rank chips; symbols). Each translation chunk turns it on for the
   areas it owns; the integration chunk turns it on for all.
4. **French typography**: no plain space before `:`, `;`, `!`, `?`, `»` or after `«` (U+202F narrow no-break space
   before `;!?»`, after `«`; U+00A0 before `:`), apostrophe `’` rather than `'`, ellipsis `…`.
5. **No hard-coded text**: a Vitest parses every `.vue` template with `@vue/compiler-sfc` (already a dependency of
   `vue`) and fails on a text node or a static `label`, `aria-label`, `title`, `placeholder` or `alt` attribute
   that contains a letter; a small allowlist for symbols. It also fails on `Intl.*('en'` outside `locales/`.
6. **Behaviour**: unit tests for `resolveLocale` (stored, browser, fallback, unsupported values), `plural`,
   `ordinal`, `formatList` in both locales; `<html lang>` set at boot.
7. Existing tests stay English: Vitest never calls `initLocale()`, and the Cypress support file sets
   `bc_locale = 'en'` before each visit so a contributor with a French browser still runs them in English.
8. One new Cypress spec in the integration chunk: a French browser (`navigator.languages` stubbed in
   `onBeforeLoad`) gets French and `lang="fr"`; the picker switches to English, the choice survives a reload.

### D5. Fallback to English

The parity test makes a French key always present, so there is no per-key fallback at runtime. Fallbacks:
unknown or unsupported stored value and unknown browser language give `en`; a failed load of the French bundle
(network) logs and mounts in English.

### D6. Bundle layout and lazy loading

- `locales/fr/` holds one file per area group (the translation chunks' units), typed by area
  (`export const presidentUi: Messages['presidentUi'] = {...}`), and `fr/index.ts` composes `messagesFr: Messages`.
  Parallel translation chunks never edit the same file.
- English stays eagerly imported (it is the type's reference and the fallback). French is loaded with
  `import('./fr')`: Vite emits it as its own chunk (about 55 kB raw, under 20 kB gzipped), fetched only by French
  visitors.
- Dark launch: `SUPPORTED_LOCALES` stays `['en']` until the integration chunk adds `'fr'`, so a half-translated
  bundle is never selected by detection.

### D7. Typography and length

- French text is about 20 to 30 % longer. Places at risk at 375 px: the Bataille Corse Send/Slap row
  ("Envoyer"/"Taper"), the Kobo action bar, the President action bar and hint line, lobby option rows, game picker
  cards, end-of-game overlay buttons, the Mangola placement editor toolbar.
- The integration chunk does a before/after pass with the `ui-change` skill in French, at 375 px and 1280 px, on
  each game screen, the picker, the home and the lobby; fixes are CSS (wrapping, `min-width: 0`) or a shorter
  French wording, never a smaller font.
- Typography rules are the D4 test; numbers keep their digits ("32 graines").

### D8. Privacy page, title and meta

- The privacy page is translated like any area, then reviewed word by word by the owner: it must keep describing
  only what the site really does. The infrastructure chunk adds `bc_locale` to the English "What your browser
  stores" paragraph in the same PR that starts writing it; the French privacy chunk starts after that merge.
- `<html lang>` follows the locale (screen readers pick their voice from it). The title stays `CardGamesZone` in
  both languages unless the owner wants a translated or per-page title (Q8). No meta description is added.

### D9. Translation authoring and register

- An agent drafts the French area by area from the glossary below; the owner validates the glossary **before** the
  translation chunks start (one decision gate), then reviews each translation PR (prose of the rules and the
  privacy page first).
- Register: **"vous"** everywhere is proposed (one register for the rules, the prompts and the privacy page, and
  neutral for an unknown audience). "Tu" reads friendlier in a game. Owner decision (Q1).

## Glossary (proposed French, to settle with the owner)

| English | Proposed French | Note |
|---------|-----------------|------|
| Bataille Corse | Bataille corse | French usage puts the adjective in lower case; brand capital kept if the owner prefers |
| Bullshit (game) | Menteur | the usual French name; "call bullshit" = « Menteur ! » |
| Kobo | Kobo | proper noun |
| President (game) | Président | |
| Morris / Three Men's Morris / Six Men's Morris | Marelle / Marelle à trois / Marelle à six | Tapatan, Achi unchanged |
| Mangola | Mangola | proper noun |
| President titles: President, Vice-President, Neutral, Vice-Scum, Scum | Président, Vice-président, Neutre, Vice-trou-du-cul, Trou-du-cul | traditional names; family-friendly option: Vice-pauvre, Pauvre (Q2) |
| slap (Bataille Corse) | taper (sur le tas), une tape | wrong slap = tape ratée |
| send (Bataille Corse) | envoyer | |
| pile (central) | tas | Bullshit and President also "tas"; Kobo: pioche / défausse |
| doubles, sandwich, sum of ten, tens | doublé, sandwich, somme de dix, dix | |
| honour cards | têtes (valet, dame, roi, as) | "cartes d'honneur" also understood |
| claim (Bullshit) | annonce, annoncer | truthful / bluff = sincère / bluff |
| trick, lead, pass | pli, entamer, passer | |
| pair, three of a kind, four of a kind | paire, brelan, carré | "9 or nothing" = « 9 ou rien » |
| equal play | jeu égal | |
| exchange | échange | |
| draw pile, discard pile (Kobo) | pioche, défausse | |
| power, peek, swap (Kobo) | pouvoir, regarder, échanger | matching discard = défausse en double; announce Kobo = annoncer Kobo |
| penalty card | carte de pénalité | |
| seeds, pit, sow, relay, capture (Mangola) | graines, case, semer, relais, prise / capturer | "case" matches the owner's "règle de la case vide" |
| placement (Mangola, Morris) | placement | |
| piece, point, slide (Morris) | pion, point, glisser | |
| card ranks Jack, Queen, King, Ace | Valet, Dame, Roi, As | card name: « {rank} de {suit} » (« Dame de pique ») |
| suits hearts, diamonds, clubs, spades | cœur, carreau, trèfle, pique | |
| rank chips J, Q, K, A | J, Q, K, A (kept) | the card faces show English indices; V, D, R if the owner prefers (Q5) |
| bot | bot | "ordinateur" or "IA" possible |
| bot levels Easy, Medium, Hard, Expert | Facile, Moyen, Difficile, Expert | |
| Bataille Corse tiers Training ... Legend | Entraînement, Bronze, Argent, Or, Platine, Diamant, Champion, Challenger, Légende | |
| lobby, host, seat | salon, hôte, place | |
| play again, forfeit, resign | rejouer, forfait, abandonner | |
| public / private game, invite link | partie publique / privée, lien d'invitation | |
| rules, how to play | règles, comment jouer | |

## Open questions for the owner

1. **Register**: "vous" (proposed) or "tu"?
2. **Glossary**: game names (Menteur, Bataille corse, Marelle), President titles (traditional or family-friendly),
   "1er" or "1re" in standings, any term above.
3. **Language picker**: placement and look through a Claude Design canvas (2 or 3 options on the real picker and
   home screens) before chunk L starts.
4. **Switching reloads the page** (proposed), including inside a game (a reconnection), or the picker only outside
   games?
5. **Rank letters** on rule chips and card labels: keep J/Q/K (matches the card faces) or V/D/R?
6. **Bullshit shortcuts** `d` (discard) and `b` (bullshit) lose their mnemonic in French: keep them, or change the
   defaults for French players (a stored choice is never overwritten)?
7. **Privacy page**: the owner reviews the French text word by word; both versions describe the same facts.
8. **Title**: keep `CardGamesZone` in both languages?

## Consequences

- Every future text change touches `Messages.ts`, `en.ts` and the French area file; the parity test enforces it.
  The project `CLAUDE.md` rule "every new visible text goes through `frontend/src/locales/`" gains "in English and
  French".
- Cypress specs keep asserting English text (pinned `bc_locale`).
- The backend loses `defaultName()`, `botName()` and the renumbering of names; the lobby and public list show
  "Player N" / "Joueur N" from the frontend.
