# Isolated Claude Environment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a `.claude/settings.json` that auto-approves safe dev commands and gates destructive ones behind explicit user approval.

**Architecture:** A single project-level settings file checked into the repo. Claude Code reads it automatically on every session. No runtime code — just configuration. Worktree isolation (already in place) handles file-level safety; this file handles command-level safety.

**Tech Stack:** Claude Code `settings.json` permission system.

---

## File Map

| Action | Path | Purpose |
|--------|------|---------|
| Create | `.claude/settings.json` | Command permission allowlist/denylist |

---

### Task 1: Create `.claude/settings.json` with permissions

**Files:**
- Create: `.claude/settings.json`

- [ ] **Step 1: Create the `.claude` directory and settings file**

```bash
mkdir -p .claude
```

- [ ] **Step 2: Write `.claude/settings.json`**

Create the file with the following exact content:

```json
{
  "permissions": {
    "allow": [
      "Bash(cd*)",
      "Bash(ls*)",
      "Bash(dir*)",
      "Bash(pwd*)",
      "Bash(Get-ChildItem*)",
      "Bash(Set-Location*)",
      "Bash(cat *)",
      "Bash(head *)",
      "Bash(tail *)",
      "Bash(type *)",
      "Bash(git status*)",
      "Bash(git log*)",
      "Bash(git diff*)",
      "Bash(git add*)",
      "Bash(git commit*)",
      "Bash(git checkout*)",
      "Bash(git push*)",
      "Bash(mvn*)",
      "Bash(npm*)",
      "Bash(docker-compose*)",
      "Bash(docker build*)",
      "Bash(docker logs*)",
      "Bash(docker ps*)"
    ],
    "deny": [
      "Bash(rm*)",
      "Bash(rmdir*)",
      "Bash(del *)",
      "Bash(Remove-Item*)",
      "Bash(docker system prune*)",
      "Bash(docker volume prune*)",
      "Bash(docker volume rm*)",
      "Bash(git reset --hard*)",
      "Bash(git push --force*)",
      "Bash(git clean*)",
      "Bash(*prod*)"
    ]
  }
}
```

- [ ] **Step 3: Verify the file is valid JSON**

```bash
cat .claude/settings.json | python -m json.tool
```

Expected: the JSON is printed back without errors.

- [ ] **Step 4: Commit**

```bash
git add .claude/settings.json
git commit -m "feat: add Claude Code permission controls for dev environment isolation"
```

---

### Task 2: Verify settings are picked up by Claude Code

**Files:**
- Read: `.claude/settings.json`

- [ ] **Step 1: Confirm the file exists at the correct path relative to repo root**

```bash
ls .claude/settings.json
```

Expected: file is listed.

- [ ] **Step 2: Manually test that a denied command triggers a prompt**

In a new Claude Code session in this repo, ask Claude to run:

```
rm test-dummy.txt
```

Expected: Claude Code shows a permission prompt (not auto-executes).

- [ ] **Step 3: Manually test that an allowed command runs without prompt**

Ask Claude to run:

```
git status
```

Expected: runs immediately without a permission prompt.

---

## Notes

- The `*prod*` deny pattern blocks any command string containing "prod" — this includes `docker-compose -f docker-compose.prod.yml up`. This is intentional.
- To expand the allowlist later, edit `.claude/settings.json` and add entries to the `allow` array following the same `Bash(<command>*)` pattern.
- These settings apply to every worktree that shares this repo, since the file is tracked in git.
