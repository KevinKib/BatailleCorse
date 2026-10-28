# E2E cost plan (issue #312, items 3 to 6)

Date: 2026-10-28. Written by Claude Opus 5.5. Tracking: #312 (owner's plan) and #28. Evidence:
`docs/architecture/e2e-cypress-report.md` (the report). Owner's go-ahead: "tu peux lancer quand tu veux".

## Where we stand

| #312 item | Status (checked on 2026-10-28 with `gh` and `origin/main` at `b787ecb8`) |
|---|---|
| 1. Agents run only the specs of their feature, once, one lone rerun | done (#313; `CLAUDE.md` "Tests", `docs/harnesses.md` 2026-10-24) |
| 2. Prebuilt backend jar and `vite preview` for the e2e stack | done (#314): stack ready in 24 to 27 s instead of 3 min 39 s warm / 12 min 49 s cold; 6 specs 15 min 11 s to 8 min 57 s of Cypress |
| 3. Measure for two weeks | not started: nothing records Cypress time or failures, so there is nothing to measure with |
| 4. 6 to 8 smoke specs, possibly in CI | this plan, chunk A |
| 5. Geometry checks as component tests | this plan, chunk E (pilot) |
| 6. Configurable, longer forfeit grace; retries with logging | this plan, chunks B and C |

Facts the plan relies on (read in the repository on 2026-10-28):

- 89 specs in `frontend/cypress/specs`; `cypress.config.ts` has no retries, no reporter, no component block.
- CI (`.github/workflows/ci.yml`) runs backend `mvn -B test` and frontend `npm test` + `vite build`; the frontend job
  installs with `CYPRESS_INSTALL_BINARY=0`. No e2e.
- `frenchcards` resolves in the CI backend job through `actions/setup-java` (`server-id: github`, password
  `GITHUB_TOKEN`, `packages: read`). The generated `~/.m2/settings.xml` holds `${env.GITHUB_TOKEN}`, not the token.
- `docker-compose.e2e.yml` builds the backend with `deploy/docker/settings.xml` as a BuildKit secret
  (`maven_settings`), so the settings file only exists at build time and never in an image layer.
- `vite.config.mjs` proxies `/api` and `/connect` to `http://backend:8080` for both `server` and `preview`: the
  frontend only works on the Compose network.
- The forfeit grace is already a property (`presence.forfeit-grace-seconds`, read in `AppConfig`), 5 s in
  `application-test.properties`; Spring's relaxed binding also reads `PRESENCE_FORFEITGRACESECONDS`.
- 12 specs reload the page; 10 specs mention a forfeit (`bataillecorse-lobby`, `bullshit-exit-to-games`,
  `bullshit-turn-and-a11y`, `kobo-round-and-end`, `mangola-game`, `mangola-presence`, `mangola-rematch`,
  `morris-game`, `president-exchange-sync`, `shared-lobby`). `morris-game` "survives a reload" fails on `main`
  under the 5 s grace (report, item 7).

## Key decisions

1. **Measure first, inside the chunks, not as a two-week pause.** Chunk C adds a run log (one JSON line per
   `cypress run`: specs, durations, failures, retried tests) written locally and uploaded by CI. The two-week
   measurement of #312 item 3 then runs on real data while the other chunks land, and its read-out is the gate for
   any deletion or for making the CI e2e job required. Nothing in this plan deletes a spec.
2. **A smoke subset of 8 specs runs in CI as a separate, non-required job, on the existing
   `docker-compose.e2e.yml`.** Same images agents use, so CI checks the stack too. `frenchcards` resolves in the
   Docker build by writing `deploy/docker/settings.xml` in the job from `GITHUB_TOKEN` (the setup-java file cannot
   be reused: its `${env.GITHUB_TOKEN}` is not set inside the build). The job turns required only after two weeks
   of the run log show it green at least 9 runs in 10 on `main`.
3. **The test profile's forfeit grace becomes 30 s, and forfeit specs shorten it for their own game** through a
   test-profile-only endpoint. Reloads on a slow stack stop counting as leaving; forfeit specs keep their speed.
   The rule stays in `sessionmanagement.presence` (a per-game grace override), the endpoint in `presentation/` under
   `@Profile("test")`, like `KoboTestDealController`.
4. **Retries `runMode: 1` only together with a log of every retried test.** A flake no longer costs an agent a
   rerun, but it stays visible (flakes are what revealed the STOMP races, report 3.2).
5. **Geometry guards move to Cypress component tests, one pilot first.** Same browser layout engine, no backend,
   no STOMP, seconds per spec; component tests need no stack, so they run in the CI frontend job. One game is
   migrated in this plan (the pilot, Six Men's Morris stillness); the other games follow only if the pilot's
   numbers hold. The e2e spec of a migrated guard keeps its nominal scenario.

Spec hygiene, applied to every chunk and written into `CLAUDE.md` by the integration chunk (owner's constraint:
**new Cypress tests cover the nominal path only**):

- A new or extended e2e spec covers the nominal path of its feature; variants, edge cases and rule checks go to
  Vitest or to the backend domain tests; a layout check for one fixed state goes to a component test once
  chunk E has landed.
- No scratch, capture or debug spec committed (`zz-*`, `scratch-*`, `*-capture`, `DBG` probes), no `.only`, no
  `it.skip` without an issue number. Guarded mechanically by chunk D.
- Each spec creates its own room and closes its scripted STOMP players; no fixed `cy.wait(ms)` except a named
  settle window with a comment.
- Measurements wait for the route fade and the animation to settle (`#242`, `#245`).

## Chunks

```
P0 (shared interfaces, < 30 min)
 ├─ A  CI smoke job          ┐
 ├─ B  forfeit grace         │ in parallel, one worktree and one sub-agent each
 ├─ C  run log and retries   │
 ├─ D  spec hygiene guard    │
 └─ E  component-test pilot  ┘
I  (integration: merge, docs, all suites, smoke run, branch review)
```

### P0: shared interfaces (sequential, first)

Scope, nothing deleted:

- `frontend/cypress/smoke-specs.json`: the smoke list (decision 2), the single source for `cy:smoke` and CI:
  `create-game`, `send-card` (Bataille Corse), `shared-lobby`, `rehydrate-game`, `kobo-table`, `mangola-game`,
  `president-game`, `bullshit-table-and-lobby`. `morris-six` stands in for Six Men's Morris until chunk B fixes
  the `morris-game` reload; the integration chunk swaps them.
- `frontend/package.json`: `cy:smoke` (runs the list with `--spec`) and `cy:component`
  (`cypress run --component`).
- `frontend/cypress/runLog.ts`: `registerRunLog(on, config)`, a no-op stub with the record type (spec, duration
  ms, tests, passes, failures, retried test titles, branch, commit, date); registered in `setupNodeEvents`.
- `frontend/cypress.config.ts`: `retries: { runMode: 1, openMode: 0 }`, the `registerRunLog` call, and a
  `component` block (`framework: 'vue'`, `bundler: 'vite'`, `specPattern: 'cypress/component/**/*.cy.ts'`) with
  an empty `cypress/support/component.ts` and `cypress/support/component-index.html`.
- `frontend/.gitignore`: `cypress/results/`.

Cost: under 30 minutes, no stack. Risk: low; the `component` block must not change `cypress run` (e2e); checked by
`npm test`, `vite build` and `npx cypress verify` only. Agent-time gain: none by itself; it lets A to E run without
touching the same lines.

### A: CI smoke job (parallel)

Files: `.github/workflows/ci.yml` only.

- New job `e2e-smoke` on `pull_request` and `push` to `main`, `paths-ignore` on `docs/**` and `**/*.md`, not
  required.
- Steps: checkout; write `deploy/docker/settings.xml` (server `github`, username `github.actor`, password
  `secrets.GITHUB_TOKEN`) with `umask 077`; `docker compose -f docker-compose.e2e.yml up --build -d --wait`;
  `setup-node` 20 with npm cache and `~/.cache/Cypress` keyed on `package-lock.json`; `npm ci`;
  `npm run cy:smoke`; always `docker compose ... logs` and `down`; upload screenshots and
  `cypress/results/` as an artifact.
- Docker layer cache: none in the first version; measure the cold build in the first runs. If the job exceeds 15
  minutes, a follow-up adds `cache_from`/`cache_to: type=gha` in a CI-only Compose override.

Cost: 1 to 2 hours of agent time, most of it waiting for CI runs (the only way to test a workflow). Risks:
`GITHUB_TOKEN` might not reach the `frenchcards` package from inside Docker (inferred low: the same token already
resolves it in the backend job, only the file differs); runner time per PR about 10 to 15 minutes (inferred:
Maven `go-offline` 2 to 4 min, frontend image 2 min, 8 specs about 5 min); a flaky smoke spec makes CI noise,
hence not required. Agent-time gain: integration chunks stop replaying the smoke specs locally (about 10 to 15
min each, inferred from #314's 6-spec run); stale specs are flagged on the PR that breaks them instead of by the
next agent (about 12 maintenance cases in the report, each 20 to 60 min of a confused agent, inferred).

### B: forfeit grace (parallel)

Files: `backend/.../sessionmanagement/presence/` (per-game override), a new `@Profile("test")` controller in
`sessionmanagement/presentation/`, `backend/src/main/resources/application-test.properties` (5 to 30), the
forfeit specs that wait for a forfeit (up to the 10 listed above) and `frontend/cypress/support/commands.ts` (one
`cy.shortenForfeitGrace(seconds)` command), `morris-game.cy.ts` (reload test back to green).

- TDD: a presence unit test (override shortens the deadline of that game only), an `*IT` for the endpoint
  (present under `test`, absent under the default profile), then the spec changes.
- Default grace in production unchanged (60 s).

Cost: 2 to 3 hours; the only chunk that touches the backend and many specs. Risks: a forfeit spec that forgets the
command now waits 30 s (slow, not red); the override must not leak to other games (unit test). Cypress: run only
the specs touched, once (rule of #312). Agent-time gain: removes the reload flakes behind #280, #292 and #296
(each a rerun or a rewritten test, 10 to 30 min, inferred) and lets `morris-game` join the smoke list.

### C: run log and retries (parallel)

Files: `frontend/cypress/runLog.ts` (fill the stub), `frontend/cypress/runLog.test.ts` (Vitest on the record
builder), `frontend/scripts/cypress-run-summary.mjs` (reads `cypress/results/run-log.jsonl`, prints per spec:
runs, failures, retries, median duration; the two-week read-out).

- `after:run` appends one line per run; retried tests come from `attempts.length > 1` in the run results and are
  also printed at the end of the run, so a retry is never silent.

Cost: 1 to 2 hours, no stack except one short smoke run to see a real line. Risk: low; a Cypress major upgrade may
change the results shape (guarded by the Vitest on a recorded sample). Agent-time gain: a transient failure is
retried by Cypress instead of an agent rerun (each rerun 2 to 12 min plus reading the output; at least 14 of 108
failed runs were environmental, report 3.4); agents report Cypress time from the log instead of reconstructing it;
#312 item 3 becomes measurable.

### D: spec hygiene guard (parallel)

Files: `frontend/src/cypressSpecHygiene.test.ts` (Vitest reading `cypress/specs` and `cypress/component`): fails
on a forbidden file name (`zz-`, `scratch-`, `-capture`), `.only(`, a `DBG` probe, or `it.skip` / `describe.skip`
without `#<number>` on the line; checks every name in `smoke-specs.json` exists.

- Existing offenders (if any, for example `kobo-shared-animation-capture`) are listed in an allow-list in the test
  with the reason, not renamed or deleted here; the owner decides.

Cost: under 1 hour, no stack. Risk: low. Agent-time gain: small but certain: 10 of the 108 failed runs were debug
probes or scratch specs (report 3.4), and a broken smoke list fails in Vitest in seconds instead of in CI.

### E: component-test pilot (parallel)

Files: `frontend/cypress/support/component.ts` (mount with Pinia, PrimeVue, i18n and the app's global CSS),
`frontend/cypress/component/morris/*.cy.ts`, `.github/workflows/ci.yml` is **not** touched here (the integration
chunk adds the component run to the frontend job, to avoid a conflict with A).

- Pilot: the `morris-stillness` guard (board does not move when a warning appears, #296) mounted on fixed states
  at 375x667 and 1280x720. The e2e `morris-stillness` stays, reduced in the integration chunk only if the owner
  agrees.
- Measure: component spec time against the e2e spec time (from chunk C's log).

Cost: 2 to 4 hours (mount setup is the unknown: stores, router, fonts). Risk: medium; a component mounted outside
the app may lay out differently (fonts, root CSS); the pilot checks the same rectangles as the e2e spec on the
same state before claiming equivalence. Agent-time gain: for the next TDD reproduction of a layout defect (12 in
the report, the most-run specs: `president-table-fit` 31 runs up to 23 min each) a component test takes seconds
and needs no stack; inferred 20 to 40 min saved per such PR once the game is migrated.

### I: integration (sequential, last)

- Merge P0 then A to E into one branch (or check each merged PR on `main` in order).
- Swap `morris-six` for `morris-game` in `smoke-specs.json` once B is in.
- Add `npm run cy:component` to the CI frontend job (Cypress binary installed in that job only for this step).
- `CLAUDE.md` "Tests": smoke job, `cy:smoke`, `cy:component`, run log path, spec hygiene rules, owner's
  nominal-only constraint; `docs/harnesses.md`: one dated entry; `README.md` commands.
- All suites: `mvn test`, `npm test`, `vite build`, `cy:component`, and `cy:smoke` once on a local stack (the
  integration chunk is the one allowed to replay a family).
- Comment #312 with what landed and the date the two-week read-out is due.

Cost: 1 to 2 hours. Risk: CI noise while the smoke job settles.

## Parallelism and conflict risks

| File | Chunks | How the risk is handled |
|---|---|---|
| `frontend/cypress.config.ts` | P0 only | A to E use what P0 registered; nobody else edits it |
| `frontend/package.json` | P0 | scripts defined once in P0 |
| `.github/workflows/ci.yml` | A, I | E does not touch it; I adds the component step after A merges |
| `frontend/cypress/support/commands.ts` | B | E has its own `support/component.ts` |
| `frontend/cypress/smoke-specs.json` | P0, I (D reads it) | only the integration chunk edits the list |
| forfeit specs (`shared-lobby`, `mangola-game`, `morris-game`, ...) | B | A runs some of them unchanged; A rebases on B if B merges first |
| `CLAUDE.md`, `docs/harnesses.md`, `README.md` | I only | chunks report their rule changes in the PR body |

## Out of scope (owner decisions, after the read-out)

- Deleting or moving out of e2e the specs the report flags as probable Vitest duplicates (report section 4).
- Making `e2e-smoke` a required check.
- Migrating the geometry guards of the other games to component tests.
- Closing the coverage gaps of report 3.6 (real bot delays, other seats acting, mid-round layout states).
