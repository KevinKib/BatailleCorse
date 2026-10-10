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

## Inventory (versioned in this project)

| Item | Role |
|---|---|
| `CLAUDE.md` | Language, domain architecture rules, tracking, test suites, e2e rule, dev environment and offset-ports model |
| `frontend/vite.config.mjs` | `VITE_HMR_CLIENT_PORT`: hot reload follows the published port |
| `docs/harnesses.md` | This document |

## Known limits

- Cypress targets port 5173 by default: one e2e stack at a time unless `--config baseUrl=...` points at an offset stack.
- The offset-ports model was checked with `docker compose config` (ports and environment merge as expected) but the stack was not started.
- Docs folders flattened (chore/rename-docs-dir): specs, plans and architecture now sit directly under `docs/`; the old nested folder name and the matching skill-name prefixes in plan headers are gone.

## Reuse

Add a dated section here per iteration; keep project facts in `CLAUDE.md` and cross-project rules in the global `~/.claude/CLAUDE.md`.
