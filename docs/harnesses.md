# Claude Code harness evolutions

The harness is everything around the model: instructions (`CLAUDE.md`), skills, hooks, scripts and memory. This document traces its evolutions on BatailleCorse. Approach: spot frictions (the `/insights` report, repeated corrections), then fix them with a mechanism rather than a reminder.

## Principles

| Need | Mechanism | Why |
|---|---|---|
| Always-true rule | `CLAUDE.md` | Loaded in full each session and versioned with the project: every worktree has it. |
| Repeated multi-step recipe | Skill | Triggers on the request ("implement step...") or by hand (`/feature`). |
| Verifiable check | Hook | Mechanical: it does not depend on the model's attention. |
| Context, preferences | Memory | Only a one-line index is loaded: it cannot carry a rule. |

## 2026-10-10: project rules ported from ShodoCFI

The sibling project ShodoCFI (PR #25 and #26) got a first harness iteration. This one ports what applies to BatailleCorse.

| Friction (seen in ShodoCFI) | Fix | Where |
|---|---|---|
| No project facts for Claude (test commands, dev env, product rules, tracking) | Project `CLAUDE.md` with language, domain architecture rules, tracking, test suites, e2e rule and dev environment | `CLAUDE.md` |
| Dev stack launched on ports already taken by another session | Offset-ports model (`-p` project name plus a `ports.yml` kept in the scratchpad) | `CLAUDE.md` |
| Hot reload pointing at the other session's stack when the port differs | `VITE_HMR_CLIENT_PORT` read by `hmr.clientPort` (default 5173, unchanged behaviour) | `frontend/vite.config.mjs` |
| Fixed-port e2e colliding with a running stack | Rule: stop (never remove) the other stack, run, tear down, restart; or point Cypress at an offset stack with `--config baseUrl=...` | `CLAUDE.md` |
| Harness changes not traced | This document | `docs/harnesses.md` |

### Ported or not

| ShodoCFI improvement | Decision | Reason |
|---|---|---|
| Project `CLAUDE.md` | Ported, rewritten | Test/dev commands, hexagonal rules and tracking issues are BatailleCorse's own. |
| Offset-ports model for parallel dev stacks | Ported, adapted | BatailleCorse publishes backend 8080, debug 5005 and frontend 5173, no Postgres or OIDC. |
| `VITE_HMR_CLIENT_PORT` in Vite config | Ported | Same Vite setup, same problem. |
| `dev-data/seed.sql` and `load-test-data.sh` | Not ported | No database: state is in memory, nothing to seed. |
| Strict "Hors CFI" and Shodo pink rules, `questions-po.md`, PO backlog issues | Not ported | Specific to the CFI product. |
| Roadmap in GitHub issues, no status PR | Ported | Roadmap issue #1 already exists; the rule is added. |
| `docs/harnesses.md` | Ported, shortened | Traces BatailleCorse only; global parts stay in the ShodoCFI document. |
| Global harness (`~/.claude`: hooks `require-worktree`, `branch-naming`, `session-context`, skills `feature`, `handoff`, `roadmap-orchestrator`, `ui-change`, rtk) | Nothing to do in this repo | Already global: they apply to BatailleCorse as is. Tracked in `docs/harnesses.md` of ShodoCFI. |

## 2026-10-10: environment lifecycle (global rule)

Evolution of the user's global harness. The rule was added to the user's global `~/.claude/CLAUDE.md` (outside this repo, not versioned); this entry only records it. Recorded here because it governs how sessions use the dev environment described in `CLAUDE.md`.

| Friction | Fix | Where |
|---|---|---|
| Dev environments created for work with nothing to test by hand, and left running after the PR is merged | Environment lifecycle: dev environments are created only for stories with a feature testable by hand (UI, user journeys), never for technical work (refactors, CI, tests, docs, backend with no visible effect); once the matching PR is merged, the session closes its environment (`docker compose -p <project> down`, without `-v`) without asking, after checking with `gh` that the PR is merged and with `docker ps` that the project is its own; never closes the environment of an open PR or of another session without agreement | Global `~/.claude/CLAUDE.md`; summarised in the dev environment section of `CLAUDE.md` |
| Worktrees and Docker stacks of merged PRs piled up (25 worktrees, many stopped containers) | Cleanup on merge: when the user says a PR is merged, the session checks with `gh`, then removes its worktree (after checking it has no uncommitted change or unpushed commit) and its local branch (`git branch -d`), and closes its Docker project (`docker compose -p <project> down --remove-orphans`, without `-v`, project found through the `working_dir` label); never touches worktrees or containers of other sessions or open PRs, nor the remote branch | Global `~/.claude/CLAUDE.md` |
| Architecture specs and plans were written on the default (Sonnet) model | Model rule: the session uses Opus to orchestrate and to write or review specs and plans (plan mode when it is the session itself, `model: "opus"` on subagents that write or review specs, plans and architecture reviews); implementation (TDD, bounded fixes) may stay on the default model; the session states which model produced a plan when presenting it | Global `~/.claude/CLAUDE.md` |
| Planned PRs were tracked under plan labels (PR0, PR1…), which the owner had to map to GitHub numbers by hand | Draft-PR rule: for planned work, the subagent pushes an empty commit and opens a draft PR as soon as it starts (GitHub refuses a PR with no diff), fills it in and marks it ready at the end; reports cite `#<number>`. Kept out of the global `CLAUDE.md` on purpose (it only matters when orchestrating multi-PR plans, so it should not be loaded in every session) | User skill `roadmap-orchestrator` (Phase 2, steps 1 and 5) |

## 2026-10-14: game conventions and "Add a game" checklist

Actions R1 and R2 of `docs/architecture/new-game-retrospective-three-men-morris.md`.

| Friction | Fix | Where |
|---|---|---|
| Each plan invented its own names for the same things (F5); one game had four identifiers | One kebab-case game id and its derived forms (`<pkg>`, `<Pascal>`, `<camel>`) for every layer, legacy names listed, choices to confirm listed | `docs/architecture/game-conventions.md` |
| Shared extension points forgotten silently (F6) and `context-map.md` / `CLAUDE.md` behind (F9) | Executable checklist: PR order with one entry PR, every shared file with "fails if forgotten" or "silent", tests, determinism seam, TDD commit order, docs; context map and game list refreshed; one pointer line in `CLAUDE.md` | `docs/architecture/add-a-game.md`, `docs/architecture/context-map.md`, `CLAUDE.md` |

## 2026-10-24: agents run only the Cypress specs of their feature

Priority 1 of #312, from `docs/architecture/e2e-cypress-report.md`.

| Friction | Fix | Where |
|---|---|---|
| Cypress is 26 % of agents' active time (42 % with stack waits) and caught no regression after the fact; agents replayed whole spec families | An agent runs only the specs of the feature it touched (those its brief names as new or updated), once; a failing spec is rerun alone at most once to tell a load flake from a real failure; a whole family (all `president-*`, all `kobo-*`) is replayed only by the integration chunk of a plan or when the owner asks; the report lists the specs run and the total Cypress time. No e2e coverage is deleted | `CLAUDE.md` (Tests section), `docs/architecture/e2e-cypress-report.md` (next steps) |

## 2026-10-24: faster e2e stack (prebuilt backend, vite preview)

Issue #312, priority 2 (`docs/architecture/e2e-cypress-report.md`: the stack wait was 16 % of the agents' active time,
and a cold backend once never answered within 18 minutes).

| Friction | Fix | Where |
|---|---|---|
| The e2e backend compiled and started with `mvn spring-boot:run` inside its container: 5 to 13 minutes from an empty Maven volume, beyond the healthcheck window | The e2e backend is the production image (`target: run`): jar built during `docker compose build` with `settings.xml` as a build secret, started with `java -jar`, same healthcheck | `docker-compose.e2e.yml`, `backend/Dockerfile` |
| The BuildKit cache mount of the Maven repository was emptied between two builds (Docker Desktop's GC caps cache mounts and build contexts at 2.7 GiB), so each build downloaded about 710 artifacts again | Dependencies resolved in an image layer keyed on `pom.xml` (`dependency:go-offline`), then the sources are compiled with `-Dmaven.test.skip=true` | `backend/Dockerfile` (also the production build) |
| Cypress hit the Vite dev server: 355 requests and 4 to 14 s per cold page under load, plus a polling watcher | The e2e frontend serves the production build with `vite preview`, with the dev server's container port and its `/api` and `/connect` proxy (guarded by `src/viteConfig.test.ts`) | `frontend/Dockerfile` (target `e2e`), `frontend/vite.config.mjs` |
| Screenshots written by Cypress and spec edits invalidated `COPY . .` and rebuilt the bundle | `cypress/`, `cypress.config.ts` and `*.test.ts` left out of the frontend build context | `frontend/.dockerignore` |

Measured on 2026-10-24 on the development laptop with four other sessions' stacks running (16 cores):

| Start (`up --build -d --wait`) | Former stack | New stack |
|---|---|---|
| Cold (empty Maven repository) | 12 min 49 s until the backend answered (Maven in the container, then a 125 s Spring dev start); `--wait` gave up at 7 min 07 s, backend unhealthy | backend image 3 min 09 s (dependency layer 2 min 42 s, once per `pom.xml` change); 1 min 53 s for the first version, whose cache mount still held part of the repository |
| Backend code changed | 3 min 39 s with a warm Maven volume (the container compiles at every start) | 21 s backend image build, then the start below |
| Nothing changed | 3 min 39 s (same) | 24 to 27 s until both healthchecks pass (Spring starts in 5.5 s) |
| Frontend code changed | no rebuild (bind mount), but every page transformed on the fly | 22 s bundle rebuild, then the start above |

Same six specs, once each, on each stack (`president-game`, `kobo-animations`, `bullshit-pile-animation`,
`morris-game`, `president-stillness`, `mangola-animation-speed`): 50 of 50 tests green on both; Cypress time 15 min 11 s
on the former stack (1:19, 0:34, 0:24, 1:49, 9:50, 1:12) against 8 min 57 s on the new one (1:00, 0:13, 0:05, 0:33,
6:40, 0:23).

The developer stack (`docker-compose.dev.yml`, `dev` targets) is unchanged. A source change reaches the e2e stack only
through `up --build`; `VITE_HMR_CLIENT_PORT` has no effect there (still written by `scripts/free-ports.sh`, harmless).

## 2026-10-25: frontend spacing skill and geometry helper

Owner request: spacing is a recurring problem. Every defect was missed by the tests and only seen in manual play: the
Privacy link too close to the bottom of the picker card, the "Private game" hint glued to the next option so both read
as one text, the Mangola speed selector touching "Your side", and earlier crowding fixes on Kobo, President, the picker
and the lobbies.

| Friction | Fix | Where |
|---|---|---|
| No written spacing rules: the `--space-n` scale in `App.vue` was used by game screens only, lobby and picker wrote raw px, and nothing said how far a hint sits from its control versus from the next setting | Project skill: the existing scale and its adoption, rules (parent-owned `gap`, proximity grouping, minimum separation, `aria-describedby` and fieldset grouping, 44 px targets, long text, safe areas, 375x667 / 390x844 / 1280x720 and fit-without-scrolling), a pre-PR checklist and the pitfalls above | `.claude/skills/frontend-spacing/SKILL.md`, pointer section in `CLAUDE.md` |
| Specs checked presence, never distance, so collisions passed | Reusable Cypress helper `expectGap(a, b, minPx)`, `expectNoOverlap(a, b)`, `atSpacingViewports(check)`; pure geometry (`gapBetween`, `overlaps`) unit-tested in Vitest. Used inside the spec of the feature being written, nominal cases only, no spacing-only spec | `frontend/cypress/support/spacing.ts`, `frontend/cypress/support/spacing.test.ts` |

How to use: any frontend change that adds a component, control, hint or text block, or touches layout or spacing,
loads the skill, runs its checklist and adds the gap assertions for the new neighbours to its feature spec. Public
skills considered and not adopted: generic UI/design guideline skills (Vercel Labs web design guidelines, Anthropic
frontend-design, community "web interface design" / "Refactoring UI" skills) give rules but no project scale and no
measurement; nothing was installed.

## 2026-10-28: e2e cost plan, integration (#312)

Plan `docs/plans/2026-10-28-e2e-cost-plan.md`: chunks P0, A to E merged, then this integration chunk.

| Friction | Fix | Where |
|---|---|---|
| Cypress is the largest agent cost and nobody could see which specs are slow, flaky or useless | CI job `E2E smoke (Cypress)` plays a nine-spec smoke subset on the e2e stack (not yet a required check); a run log (one JSON line per spec) and `npm run cy:summary` give per-spec runs, failures, retries and median time; one retry in run mode only | `.github/workflows/ci.yml`, `frontend/cypress/smoke-specs.json`, `frontend/cypress/runLog.ts`, `frontend/scripts/cypress-*.mjs`, `frontend/cypress.config.ts` |
| Layout guards cost a full stack and minutes per run | Cypress component tests (pilot: Six Men's Morris stillness), run by `npm run cy:component` in the CI frontend job, where the Cypress binary is installed for that step only | `frontend/cypress/component/`, `.github/workflows/ci.yml` |
| Scratch specs, `.only` or unreferenced skips slip into the suite; specs grow beyond the nominal path | Vitest guard `specHygiene.test.ts`; rule in `CLAUDE.md`: new Cypress tests cover the nominal path only, the rest goes to Vitest or the backend domain tests | `frontend/cypress/specHygiene.test.ts`, `CLAUDE.md` |
| `morris-six` in the smoke list while the forfeit grace was broken | Swapped for `morris-game` now that the grace fix is in | `frontend/cypress/smoke-specs.json` |

Why: `docs/architecture/e2e-cypress-report.md` showed Cypress at 26 % of agents' active time; CI is now the Cypress verification of a PR, so agents no longer replay specs locally. Left to the two-week read-out of the run log: deleting specs and making the smoke job a required check.

## Inventory (versioned in this project)

| Item | Role |
|---|---|
| `CLAUDE.md` | Language, domain architecture rules, tracking, test suites, e2e rule, dev environment and offset-ports model |
| `frontend/vite.config.mjs` | `VITE_HMR_CLIENT_PORT`: hot reload follows the published port; `preview` serves the e2e build with the dev proxy |
| `docker-compose.e2e.yml`, `backend/Dockerfile` (target `run`), `frontend/Dockerfile` (target `e2e`) | E2E stack from prebuilt images: jar and production bundle |
| `docs/harnesses.md` | This document |
| `.claude/skills/frontend-spacing/SKILL.md`, `frontend/cypress/support/spacing.ts` | Spacing rules, pre-PR checklist and gap / overlap assertions for frontend layout changes |
| `docs/architecture/add-a-game.md`, `docs/architecture/game-conventions.md` | Checklist and naming rules an agent follows to add a game |
| `.github/workflows/ci.yml` | GitHub Actions: backend tests, frontend tests + build + Cypress component tests, and the e2e smoke job, on push to `main` and on pull requests |

## Known limits

- Cypress targets port 5173 by default: one e2e stack at a time unless `--config baseUrl=...` points at an offset stack.
- The offset-ports model was checked with `docker compose config` (ports and environment merge as expected) but the stack was not started.
- Docs folders flattened (chore/rename-docs-dir): specs, plans and architecture now sit directly under `docs/`; the old nested folder name and the matching skill-name prefixes in plan headers are gone.

- Backend `*IT` classes now run in `mvn test` (chore: surefire `includes` in `backend/pom.xml`; they were silently skipped by surefire's default patterns, locally and in CI). `CLAUDE.md` Tests section and Java version note updated. `BatailleCorseWebSocketControllerIT` stays `@Disabled` (stale, with reason).

## Reuse

Add a dated section here per iteration; keep project facts in `CLAUDE.md` and cross-project rules in the global `~/.claude/CLAUDE.md`.
