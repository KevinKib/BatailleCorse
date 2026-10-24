# Cypress e2e: what it costs and what it finds

Date: 2026-10-24. Scope: the Cypress suite in `frontend/cypress` (83 specs on `main` at `efb45fb0`), as the
sub-agents of the orchestration session used it between 2026-10-13 and 2026-10-21, plus the PR bodies up to
#305. Asked by the owner to judge the tests' usefulness: how much agent time goes to Cypress, and in which
cases a test found a real defect. Follows `e2e-instability-investigation.md` (2026-10-14). Tracking: #28; recommendations: #312.

Every figure is labelled:

- **measured**: recomputed for this report from the transcripts or from `gh`;
- **verified**: read in a PR body, a commit or an agent report, and cross-checked with a second source;
- **inferred**: a reading of the data that was not checked case by case.

## 1. Purpose and method

### Sources

| Source | What it gives |
|---|---|
| Sub-agent transcripts (`tasks/<agent>.output`, JSON lines with timestamps) | every Bash call that ran `cypress run` / `cy:run`, its output, and the agent's next reasoning |
| Background run outputs (`tasks/b*.output`) | the summary table of long multi-spec runs launched in the background |
| Orchestration session transcript | agent hand-backs and the first analysis (2026-10-23) |
| `gh pr list --state all` (281 PRs, up to #311) | what each PR says about Cypress: red before the fix, flakes, reruns |
| `frontend/cypress/specs` on `main` | the spec inventory |

### Extraction (measured)

- `extract.js` walks every transcript, keeps each tool call whose command contains `cypress` or `cy:run` (and is
  not a `cat`/`grep`/`ls`/`git` of a spec), and records its output: `N failing`, `N of M failed`,
  `AssertionError`, `CypressError` or `Timed out retrying` mark it as failed.
- Keeping only real runs (`cypress run` or `cy:run` in the command) gives **332 runs by 30 sub-agents, 108 of
  them with a failure**, between 2026-10-13 and 2026-10-21.
- The time split (section 2) reruns `agent-time.js` over the same 30 agents: the duration of a foreground call
  is the gap between its `tool_use` and its `tool_result`, classified by keywords (`cypress`; `docker compose`,
  `docker logs`, health loops; `mvn`/`vitest`/`vite build`; other).
- The failure signatures (section 3.4) are a keyword classification of the error lines kept in each run.
- The usefulness counts (section 3) come from the PR bodies and the agent reports, read one by one.

The scripts and extracts stay in the session scratchpad (`cy-analysis/`, `cy-report/`); they are not part of the
repository.

## 2. Time share

### Per agent (measured)

"Active" is the agent's wall-clock from first to last transcript line, minus gaps over 30 minutes during which no
foreground call was running (waits between a hand-back and a relaunch). "Stack" is time in Docker commands and
health-wait loops (cold builds included).

| Agent's PR | Cypress runs (failed) | Active min | Cypress min | Stack min | Unit suites min | Cypress % | Cypress + stack % |
|---|---|---|---|---|---|---|---|
| #292 exchange sync e2e | 11 (5) | 163 | 53 | 11 | 7 | 32 | 39 |
| #287 table stillness | 19 (6) | 360 | 64 | 40 | 11 | 18 | 29 |
| #294 texts follow animations | 10 (5) | 198 | 26 | 25 | 13 | 13 | 26 |
| #293 completion offer | 11 (5) | 249 | 38 | 55 | 16 | 15 | 37 |
| #288 soft rings | 10 (6) | 110 | 38 | 6 | 14 | 35 | 40 |
| #284 Kobo turn label | 13 (4) | 149 | 72 | 23 | 4 | 48 | 64 |
| #283 round-end sequencing | 7 (3) | 118 | 53 | 7 | 14 | 44 | 50 |
| #280 Six Men's Morris e2e | 9 (5) | 108 | 64 | 12 | 3 | 59 | 71 |
| #279 President animations 3 | 24 (10) | 254 | 50 | 22 | 18 | 20 | 28 |
| #276 Six Men's Morris UI | 13 (2) | 155 | 31 | 3 | 14 | 20 | 22 |
| #269 exchange flights | 14 (3) | 207 | 45 | 6 | 13 | 22 | 24 |
| #264 President animations 1 | 21 (0) | 149 | 39 | 3 | 13 | 26 | 28 |
| #263 two-cut counts | 6 (1) | 96 | 32 | 23 | 15 | 33 | 57 |
| #255 Kobo visual polish | 41 (10) | 285 | 54 | 125 | 3 | 19 | 63 |
| #245 kobo-turn robust label | 10 (1) | 32 | 16 | 13 | 0 | 50 | 89 |
| #241 Mangola vs bot | 7 (3) | 50 | 28 | 12 | 1 | 55 | 78 |
| #236 President entry | 15 (10) | 109 | 28 | 1 | 11 | 26 | 27 |
| #218 Kobo animation tempo | 9 (4) | 82 | 19 | 13 | 4 | 24 | 40 |
| #193 Kobo memorise and turn | 10 (0) | 27 | 1 | 1 | 4 | 3 | 5 |
| 11 other agents | 72 (25) | 848 | 217 | 212 | 60 | 26 | 51 |
| **All 30 agents, pooled** | **332 (108)** | **3749** | **968** | **613** | **238** | **26** | **42** |

- Per agent, with 5 runs or more (25 agents): Cypress takes **from 3 % to 59 %** of active time, median 32 %,
  half of the agents between 20 % and 40 % (measured).
- Pooled over the 30 agents: **26 % on Cypress, 42 % with the stack** (measured). The first estimate given on
  2026-10-23 (13 to 32 %, 26 to 39 % with the stack) came from four agents only (#292, #287, #294, #293); it sits
  inside the wider range measured here.
- Cypress costs **4 times the unit suites** (Maven, Vitest, build: 238 minutes against 968) (measured).
- A later agent outside the dataset (#297, 2026-10-23) measured 22 % on Cypress and 43 % with the stack
  (orchestrator's measurement, verified in the session transcript).

### Limits of the time measure

- **Background runs are not timed.** 52 of the Cypress calls in these transcripts ran in the background
  (`run_in_background`), mostly the long multi-spec suites; their duration is not in the table, so the share is a
  **lower bound** (measured count, inferred direction).
- **Keyword classification.** A `sleep` loop waiting for a stack counts as "other", a `docker compose up --build`
  of a cold backend counts as "stack". Both blur the split.
- **Transcripts without usable timestamps.** The 30 agents of the dataset all have timestamps, but the agents
  after 2026-10-21 (#298 to #303) have empty or timestamp-less transcripts here: the script returns no duration
  for them, so they are out of the table.
- **Model time is not split.** The rest of the active time (about 58 %) mixes model thinking, editing and other
  commands; part of the model time is spent reading Cypress output and screenshots, which this measure counts as
  "other".

## 3. Usefulness

### 3.1 Summary

| Category | Count | Status |
|---|---|---|
| Real application defect found by the e2e, that Vitest and CI had not seen | **7** (6 surfaced by flaky specs, 1 introduced by a PR and caught in that PR) | verified, one of them only inferred (see 3.2) |
| Regression introduced by another, already merged PR and caught after the fact | **0** | verified (no case found in 281 PR bodies or 332 runs) |
| TDD reproduction of a defect reported by the owner (spec red on `main`, green with the fix) | **12** (#114 to #305), plus 2 borderline | verified in the PR bodies |
| Feature spec written first (red because the feature was missing) | about 6 (#12, #153, #195, #197, #201, #205) | verified for #153, #195, #201, #205 |
| Environmental flake (load, cold stack, standby, grace) | the largest share of failed runs; at least 14 of 108 by their error text alone | measured lower bound, rest inferred |
| Test maintenance after a legitimate change, or a defect in the spec itself | about 12 | verified |
| Duplicate of Vitest or of backend domain tests | about 12 specs | inferred (specs not reread line by line) |

### 3.2 The 7 real defects found by the e2e

| # | Defect | How the e2e showed it | Fix | Status |
|---|---|---|---|---|
| 1 | A lobby command sent before the first STOMP connection was lost (`no underlying STOMP connection`) | `shared-lobby` flaked | #167: commands wait for the connection; Vitest red then green | verified |
| 2 | A late `hydrate` answer (403) after Leave redirected the player to "lost" | `shared-lobby` still flaked about 1 in 10 after fix 1 | #167: the answer is ignored if the game or token changed | verified |
| 3 | Route fades never finished: two views stayed mounted, so two Back buttons existed | `game-home` failed 3 runs in 4 (`cy.click()` on 2 elements) | #143: `routeFade.ts` reacts only to its own `transitionend`, with a fallback timeout | verified; whether a player could see it is unknown |
| 4 | The server processed and broadcast one client's STOMP frames out of order (79 to 106 of 199 renames applied out of order in a burst) | flakes led to the #192 investigation, which measured it; a Cypress workaround (`botTierIs`) had been hiding it | #203: preserve receive and publish order | verified |
| 5 | First-connection race: an update broadcast between the REST load and the first STOMP connection was lost for good (a guest could miss an arrival or an option change) | found by the #192 investigation of the flakes, reproduced in Vitest; Cypress retry loops had been hiding it | #204: re-hydrate after every STOMP connection | verified |
| 6 | Bullshit and Bataille Corse applied stale game states (no version guard) | named by the #192 investigation (race C3) | #214: version guard | **inferred**: #214 says no e2e run ever showed the disorder; only Vitest covers it |
| 7 | A stale "Your turn": taps were ignored, and a triple could be played as a pair | the existing `president-stillness` failed 4 times in the full run of the PR that introduced the bug | #294, before merge | verified |

Six of the seven came out of the **instability** of the specs (the investigation of why they flaked), not out of
a failing assertion. The seventh is the only case where an existing spec's assertion caught a real bug, and it
was inside the PR that created it.

### 3.3 TDD reproductions of reported defects

Spec written or extended first, seen red on `main`, green with the fix (verified in each PR body):

| PR | Defect | Spec | Red measure |
|---|---|---|---|
| #114 | Bullshit lobby at 6 players scrolled | `bullshit-lobby-bots` | Start and blocks off screen |
| #187 | Game picker scrolled on small phones | `game-picker` | Play hidden at 375x667 and 360x640 |
| #200 | Kobo phase title off centre | `kobo-phase-title` (and a Vitest) | 4.0 px off |
| #255 | Kobo layout moved between turn states | `kobo-stable-layout`, `kobo-visual-polish` | rectangles moved |
| #284 | Kobo turn label off the cards' axis with a button shown | `kobo-turn-label-centered` | 34.4 px off |
| #287 | President table moved at every state change | `president-stillness` | 24 of 24 red (felt +84 px, -141 px) |
| #293 | Complete never offered out of turn; out-of-turn completion not animated | `president-completion-offer` | 4 of 4 red without fix (b) |
| #294 | Texts ran ahead of the animations | `president-text-sync` | "no banner when the state arrives" |
| #296 | Six Men's Morris board shrank when a warning appeared | `morris-stillness` | board moved up to 124 px |
| #298 | President pile cards too small | `president-pile-size` | 40 px and 54 px |
| #300 | Lone pair never offered the completion out of turn | `president-completion-offer` | button never appears |
| #305 | Bullshit discard flight started from the wrong card | `bullshit-flight-frames` | different frame |

Borderline, not counted: #288 (soft rings, an owner-requested visual change rather than a defect, red first) and
#264 (phone layout problems the owner reported; new `president-table-fit` checks added, red not documented).

**These are the strongest use of Cypress in this project.** Most are geometry guards (fit, stillness,
centring, sizes): happy-dom, used by Vitest, has no layout engine, so no unit test could have measured them.

### 3.4 Failed runs: what the 108 failures were

Keyword classification of the error text kept in each failed run (measured), with its reading:

| Signature | Runs | Reading |
|---|---|---|
| Layout or geometry assertion (height, overflow, px, rect, ring) | 39 | mostly iteration on a new spec or on the fix itself, including the expected red of TDD (inferred from sampling: the soft-rings spec alone failed 6 runs in a row while it was being written) |
| No error text kept (output piped through `grep` or `tail`) | 29 | unknown |
| Other assertion or timeout | 16 | mixed: expected TDD reds (`president-text-sync`), selectors to update, timeouts |
| Page load (60 s), `ECONNREFUSED`, `ESOCKETTIMEDOUT`, `cy.visit()` failed | 8 | environment |
| Room creation or lobby not reached in time, `cy.request()` timeout | 6 | environment (cold or loaded stack) |
| Deliberate debug probe (`DBG …` asserted against `'x'` to print values) | 5 | not a test outcome |
| Scratch or capture spec (`zz-*`, `scratch-*`, `capture`) | 5 | not a test outcome |

So at least 14 failed runs are environmental by their error text alone (measured), and 10 are not test
outcomes at all. The background multi-spec suites tell the same story: **of 10 background runs of 5 specs or
more, only one was fully green** (a 6-spec rerun after the #294 fix); the others failed 1 to 6 specs, nearly always on page
loads, room creation or the first test of a file (measured from `bg.txt`). Single specs took up to 12 minutes
(`president-stillness`) and 23 minutes (`president-table-fit` on a loaded machine) (measured).

### 3.5 Causes of flakiness

| Cause | Evidence | Status |
|---|---|---|
| Page loads over 60 s under machine load (Vite dev server, 355 requests per cold page) | 8 failed runs; load average 18 to 33 on 16 cores measured on 2026-10-17; dev page load 4.2 to 14.5 s against 1.2 to 1.4 s for a production build | measured |
| Cold backend: the e2e backend compiles with Maven inside its container | `bc-presend` (#283) did not answer after 18 minutes, and the agent handed back 4 times without running Cypress; #297's backend was unhealthy after the healthcheck window | verified |
| 5 s forfeit grace in the `test` profile (`presence.forfeit-grace-seconds=5`) | a reload on a slow stack counts as leaving: `morris-game` "survives a reload" fails on `main` too (#296), #280 dropped a reload test, #292 had to keep a twin sending presence every 300 ms | verified |
| Windows standby during long loops, lid closed | #167 (Kernel-Power logs to the second), #192 | verified |
| Hot reload pointing at another stack (`VITE_HMR_CLIENT_PORT` missing) | reload loops on shifted ports (#245) | verified |
| Scripted STOMP players never closed (browser ceiling of about 32 WebSockets) | #192 measured, #196 fixed | verified |
| Spec-side races: Kobo's 500 ms settle window on the slot label; measures during the 800 ms route fade | #218 then #245; #167, #242 | verified |
| Several stacks and sessions at once; Docker Desktop once killed by an agent's `taskkill` on 2026-10-17 | all stacks down; background wait loops killed for low memory several times | verified |

### 3.6 Misses

The e2e also let defects through that the owner found by hand (verified):

- **#293**: `president-equal-play` already tested "completes out of turn", but with no bot (so not the race
  between the bot's 0.7 to 1.3 s thinking and the 1.1 s flight) and with the browser completing, never another
  seat. The Vitest used an event shape the server never sends.
- **#300**: the same completion failed for a lone pair.
- **#284**: `kobo-phase-title` only measured the state with no button; the 34 px offset with a button passed.
- **#264**: `president-table-fit` only checked the start of a round; the felt scrolled mid-round at 8 players.

And because Cypress is not in CI, specs stayed red on `main` until another agent tripped on them: the Kobo specs
after #266 (aligned by #268), `bullshit-lobby-bots` with a stale selector (#108, #113). None of these were
regressions of the application: the specs were stale.

### 3.7 Test maintenance (about 12, verified)

#268 (after #266), #108 and #113 (stale selector), #242 (measure after the fade), #245 (settle window), #250
(reload before the server had the draft), #263 (trick-variants update), #241 (Mangola lobby asserted no Add bot),
#271 (Picaria specs removed), #276 (game-home, game-picker, morris-lobby wording), #292 and #294 (longer waits
once texts follow animations), commit `b16a210e` in #262 (a non-2 card in a scripted deal).

## 4. Per-spec value

Runs and failed runs per spec family come from the 332 runs (measured); the value judgement is inferred.

| Group | Specs | Value | Why |
|---|---|---|---|
| Multi-client flows over WebSocket | `shared-lobby`, `lobby-remove-seat`, `rehydrate-game`, `create-game`, `president-exchange-sync`, `president-completion-offer`, `president-text-sync`, `president-rematch-bots`, `mangola-presence`, `mangola-rematch`, `morris-game`, `kobo-table` | high | nothing else exercises two clients, the server and the timing together; they surfaced the STOMP races |
| Scripted whole games, one per game | `president-game`, `kobo-table-turn`, `mangola-game`, `morris-six`, `bullshit-table-and-lobby`, `send-card` | high as smoke tests | they would have shown the stale specs after #266 and #108 at once |
| Geometry guards | `president-stillness`, `president-table-fit`, `president-pile-size`, `president-soft-rings`, `morris-stillness`, `kobo-stable-layout`, `kobo-turn-label-centered`, `kobo-*-fit`, `game-picker`, `font-fit`, `bataillecorse-menu-fit`, `lobby-bot-name`, `game-screens-homogeneity` | high in TDD, costly to run | they reproduced 12 reported defects; they need a layout engine but not a backend |
| Animation and capture specs | `president-animations`, `president-exchange-flights`, `kobo-animations`, `bullshit-pile-animation`, `bullshit-flight-frames`, `*-capture` | medium | useful for before/after captures and frozen frames; heavy |
| Probable duplicates of Vitest or of domain tests | `bataillecorse-hotkey-settings`, `bullshit-hotkey-settings`, `rules-panel`, `turn-indicator`, `route-fade`, `bullshit-hand-suit-sort`, `kobo-phase-title`, `kobo-slot-outline`, `president-trick-variants`, `mangola-endless` | low | the rule or the component is already covered without a browser |

The most-run specs were geometry guards under construction: `president-table-fit` 31 runs (9 failed),
`president-animations` 23 (3), `kobo-turn` 18 (5), `president-stillness` 17 (6), `game-picker` 15 (9)
(measured).

## 5. Recommendations

1. **A small e2e subset in CI (6 to 8 specs)** on a Compose stack on ubuntu: `create-game`, `shared-lobby`,
   `rehydrate-game`, and one scripted game per game (`kobo-table`, `morris-game`, `mangola-game`,
   `president-game`, `bullshit-table-and-lobby`). It would catch stale specs and cross-PR drift the day they
   happen. To check first: `frenchcards` must resolve inside the CI Docker build.
2. **Serve the e2e frontend with `vite build` + `vite preview`** instead of the Vite dev server: 65 requests
   and about 1.3 s per cold page against 355 requests and 4 to 14 s (measured on 2026-10-17 outside Cypress; the
   effect on e2e flakiness is inferred).
3. **A prebuilt backend image** for the e2e stack (built once per commit, outside the container), instead of a
   cold Maven compile in each new stack; then wait on the healthcheck.
4. **A longer forfeit grace, configurable per stack** (for example 30 s through an environment variable), so
   reload specs survive a slow page load; keep a short grace for the specs that test the forfeit itself.
5. **Retries with logging**: `retries: { runMode: 1 }` plus a log of every retried test, so flakes stay
   visible (they are what revealed the STOMP races) without blocking the run.
6. **Move geometry guards to Cypress component tests** mounted on a fixed state (no backend, no STOMP): same
   layout engine, seconds instead of minutes, deterministic. Move the probable Vitest duplicates out of e2e.
7. **Fix or rewrite the `morris-game` reload test** ("survives a reload"), which fails on `main` because the
   reload counts as leaving under the 5 s grace (#296). Item 4 likely fixes it.
8. **Close the coverage gaps seen in section 3.6**: real bots with realistic delays in President specs, actions
   by another seat (not only the browser), states with buttons and mid-round states in layout guards.

## 6. Proposed next steps

1. Track these recommendations as a checklist: #312.
2. Start with items 2, 3 and 4 (stack speed and grace): they cut the environmental failures that dominate the
   cost, and the CI subset (item 1) depends on a stack that starts reliably.
3. Then the CI subset (item 1), with retries and logging (item 5).
4. Then move the geometry guards to component tests (item 6), one game at a time, keeping the full e2e for
   multi-client flows.
5. Measure again after each step with the same scripts: share of agent time on Cypress, failed runs by
   signature, fully green suite runs.

## 7. What this report could not determine

- The real time lost to reruns: many outputs were filtered through `grep` or `tail`, and background runs are
  not timed.
- The cause of the 29 failed runs whose output kept no error text.
- Whether defect 3 (two mounted views) was ever visible to a player.
- The flake rate per spec since the #192 investigation: no new 10-run measurement was made.
- Agents after 2026-10-21: their transcripts have no usable timestamps here, so only their PR bodies were read.
