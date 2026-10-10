# Isolated Claude Environment Design

**Date:** 2026-05-18  
**Approach:** Option A — Git Worktrees + Claude Code permission controls

## Goal

Give Claude full dev access (read, edit, build, Docker) while preventing:
- **A) Accidental destructive edits** — source file deletions, hard resets, broken main branch
- **B) Runaway commands** — expensive, infinite, or production-touching operations

Scope creep (C) is considered acceptable and is not a primary concern.

## Constraints

- All infrastructure is local — no production remote endpoints or credentials on this machine
- Must support full Docker Compose workflow (dev environment)
- Windows 11 host, PowerShell + Bash available

## Component 1 — File isolation via git worktrees

Every Claude Code session runs inside a dedicated git worktree on its own branch (e.g. `claude/relaxed-cerf-54466e`). File edits never land on `main` directly. The user merges only what they approve.

This is already the standard operating mode — no implementation needed. It becomes enforced convention.

## Component 2 — Command safety via `.claude/settings.json`

A project-level settings file controls which commands Claude can run without prompting.

### Auto-approved (no prompt required)

| Category | Commands |
|---|---|
| Navigation | `cd`, `ls`, `dir`, `pwd`, `Get-ChildItem`, `Set-Location` |
| File inspection | `cat`, `head`, `tail`, `type` |
| Git (read) | `git status`, `git log`, `git diff` |
| Git (write, worktree-safe) | `git add`, `git commit`, `git checkout`, `git push` |
| Builds | `mvn`, `npm` |
| Docker (safe) | `docker-compose up`, `docker-compose down`, `docker build`, `docker logs`, `docker ps` |

### Always require explicit user approval

| Category | Commands / Patterns |
|---|---|
| Filesystem destructive | `rm`, `rmdir`, `del`, `Remove-Item` |
| Docker nuclear | `docker system prune`, `docker volume prune`, `docker volume rm` |
| Dangerous git | `git reset --hard`, `git push --force`, `git clean` |
| Production-scoped | any command matching `*prod*` or `*production*` |

### Implementation

Stored in `.claude/settings.json` at the repo root — checked into version control so it applies automatically to every Claude session in this project.

## What this does NOT cover

- Hard OS-level process isolation (that would require WSL2 or a devcontainer — Option B/C)
- Resource limits (CPU/memory caps) on runaway processes
- These can be added later by upgrading to Option B if needed
