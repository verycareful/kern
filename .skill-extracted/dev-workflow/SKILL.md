---
name: dev-workflow
description: Use when there is any sign of a software project or git repository — including file names, code snippets, imports, CMakeLists.txt, package.json, .gitignore, git status output, commit messages, changelogs, version numbers, source file extensions (.py, .cpp, .ts, .go, etc.), or any coding task of any size. Default to loading this skill whenever in doubt.
---

# Dev Workflow Skill

This skill governs how Claude assists with software development. It enforces consistent
conventions across planning, coding, versioning, testing, changelog management, and Git hygiene.

---

## 0. Golden Rules (Always Apply)

1. **Plan before coding.** Scale the plan to the task — see §1 for details.
2. **Never run destructive or mutating Git commands.** Only read-only Git commands are allowed (see §5).
3. **Refactor freely, but always explain what changed and why.**
4. **Follow language community standards** for naming, formatting, and structure.
5. **Never bump the version or update CHANGELOG unless the user explicitly requests it** (e.g. "bump the version", "update the changelog", "do the release steps"). Bug fixes and feature work → update `changes_version.md` and relevant documentation only.
6. **The `.1` patch slot is sacred** — strictly for test suites only.
7. **After every coding task**, update `changes_version.md` with one line per changed file (see §9). Provide the commit message when the user says they're ready to commit.
8. **When in doubt, ask — never assume.** When debugging, state the hypothesis in one sentence and ask for confirmation before reading any source file. Never trace through code alone when the developer can answer in five words. The failure mode to avoid: doubt → read file → analyze → doubt again → read another file. The correct loop: doubt → ask → fix.
9. **`changes_version.md` is always gitignored.** Add it on first use if missing from `.gitignore`. Clear it after every push. If anything is unclear, ambiguous, or has multiple valid interpretations, stop and ask before proceeding. State exactly what is confusing. Do not silently pick an interpretation and forge ahead.
10. **Never use em dashes (`—`, U+2014).** They cause encoding and rendering issues in some frontend stacks (React/JSX, Markdown pipelines, terminal output across mixed locales). Use alternatives instead: a colon (`:`) for an explanatory pause, a comma for a soft break, parentheses for an aside, two hyphens (`--`) for a hard break, or split into two sentences. This rule applies to *every* output channel: code, comments, docs, commit messages, PR descriptions, CHANGELOG entries, file content, and chat responses. En dashes (`–`, U+2013) are not affected, but prefer ASCII hyphens unless the typographic distinction matters.

---

## 1. Planning Phase

### What counts as a major change?
The **developer signals** when something is major (e.g. "this is a big change", "new feature", "redesign"). Claude should not second-guess this — if the developer says it's major, treat it as major.

For everything else, Claude uses judgment:
- **Minor** (typo, rename, single-function fix, small config change): short one-line confirmation, then proceed.
- **Significant but not developer-flagged major** (touches >2 files, new dependency, moderate refactor): propose a brief plan and wait for a nod before coding.

### Minor changes
1. If anything is unclear, ask first — do not assume.
2. State what you're about to do in one sentence.
3. Wait for a quick "go ahead" or equivalent.
4. Write the code.

### Significant changes (not developer-flagged major)
1. Restate the goal in your own words.
2. List files to be created or modified.
3. Note whether a `.1` test release is triggered (if the user has requested a version bump).
4. Wait for approval before writing any code.

### Major changes (developer-flagged)
Run a **Brainstorm Sprint** first (see §1a), then proceed to the full plan above.

---

## 1a. Brainstorm Sprint (Major Changes Only)

Before planning a developer-flagged major change, Claude runs a short requirement
clarification sprint to make sure it's building the *right thing*, not just building
the thing *correctly*.

### Steps

1. **Ask 2–3 targeted questions** about:
   - Intent / goal ("what problem does this solve?")
   - Constraints ("any parts of the codebase that must not change?")
   - Success criteria ("how will we know this is done?")

2. **Write a short understanding summary** (2–3 sentences max):
   > "Here's what I understood: [summary]. Does this match what you had in mind?"

3. **Wait for explicit confirmation** before moving to the planning phase.

Keep it tight — this is a sprint, not an interview. If the developer's request was
already detailed, condense or skip questions that are already answered.

---

## 2. Coding Phase

- Follow the **language's community standard** for naming, file structure, and formatting:
  - Python → PEP 8, snake_case, `src/` layout
  - TypeScript/JS → camelCase vars, PascalCase classes, kebab-case files, ESLint standard
  - Go → gofmt, short variable names, package-per-concern
  - Rust → snake_case, Clippy clean
  - etc.
- Apply **SOLID and DRY** principles throughout.
- No magic numbers — use named constants.
- Functions should do one thing. If a function needs a long comment to explain what it does, it should probably be split.
- When refactoring, always explain:
  - What was changed
  - Why (e.g. SRP violation, duplication, readability)
  - What behavior is preserved

---

## 3. Versioning (A.B.C.D scheme — new projects only)

Legacy projects keep their own versioning scheme — see §3a for how to handle them.

For all new projects, use this scheme:

```
A  =  0 | 1 | R
         0 → Alpha
         1 → Beta
         R → Release

B  =  0–∞   Major version
C  =  0–∞   Minor version
D  =  0–∞   Patch slot   (0 = initial, 1 = test suite, 2+ = real patches)
```

### Bump rules

| Change type         | Bumps | Resets         |
|---------------------|-------|----------------|
| `fix:` commit       | D     | —              |
| `feat:` commit      | C     | D → 0          |
| `BREAKING CHANGE:`  | B     | C → 0, D → 0   |
| Alpha→Beta→Release  | A     | Manual only    |

> `fix:` and other minor commit types are automatic. `feat:` and `BREAKING CHANGE:` bumps are confirmed with the developer before applying. A changes are always manual.

### Version bump checklist (only when explicitly requested)

**Do not bump the version unless the user explicitly asks.** When a version bump is requested, Claude must grep the entire working directory for the **previous version strings** and update every occurrence before presenting the commit message. Update all instances wherever appropriate. After grepping, explicitly tell the developer which files were updated and which occurrences (if any) were intentionally left (e.g. historical CHANGELOG entries).

### D slot rules

- `D = 0` is the **initial release** of a new A.B.C version — no patches yet.
- `D = 1` is **always** a test-only release (see §4). Required after every B or C bump.
- `D = 2` is the first real patch. All subsequent patches increment D normally.
- High D values are a **design smell** — many patches on a single minor version signals the software is flawed. Flag this and recommend bumping C instead.

### Examples

```
0.1.0.0   → Alpha, Major 1, Minor 0 — initial
0.1.0.1   → test suite release
0.1.0.2   → first real patch
0.1.0.3   → second patch
0.1.1.0   → new minor — initial
0.1.1.1   → test suite for 0.1.1
R.1.0.0   → Release, Major 1, Minor 0 — initial
R.1.0.1   → test suite for R.1.0
R.1.0.2   → first real patch
```

---

## 3a. Legacy Project Versioning

For projects that already have their own versioning (semver, calendar versioning, etc.):

1. **Never impose the A.B.C.D scheme.** Follow whatever the project already uses.
2. **Identify the scheme** from `package.json`, `pyproject.toml`, `CHANGELOG.md`, or similar before suggesting any version bump.
3. **Infer bump rules** from the project's existing pattern. If unclear, ask before bumping.
4. **Still enforce the `.1`-equivalent** if the project uses patch slots meaningfully — adapt the spirit of the rule to whatever scheme is in use.
5. **Still update CHANGELOG** using the format the project already uses (or Keep a Changelog if none exists).

---

## 4. Testing (.1 releases)

Every time **B or C is bumped**, a `.1` test release is mandatory before any `.2+` patches.

### Rules

- `.1` releases contain **test code only** — no features, no fixes, no refactors.
- Claude writes the test suite as part of the `.1` version.
- Tests must cover the new functionality introduced in the triggering version bump.
- Test suite should include:
  - Unit tests for new/changed functions
  - Integration tests for affected flows
  - Edge cases and known failure modes
- No `.2` patch may be started until the `.1` test suite is written and committed.

### Claude's responsibility

When a C or B bump occurs, Claude will:
1. Note that a `.1` test release is now required.
2. Write the full test suite for the new version.
3. Only then proceed to any subsequent patch work.

---

## 5. Git Rules

### ❌ Forbidden Git commands (Claude must never run these)

```bash
git commit
git push
git pull
git merge
git rebase
git reset
git checkout   # (branch switching)
git branch -d
git tag
git stash
```

### ✅ Allowed Git commands (read-only / diff inspection)

```bash
git diff
git diff --staged
git log --oneline
git log --graph
git status
git show
git blame
git ls-files
```

### Commit messages (Conventional Commits)

Format:
```
<type>(<optional scope>): <short description>

[optional body]

[optional footer: BREAKING CHANGE: ...]
```

Types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`, `style`, `ci`, `perf`

Claude writes the commit message and presents it to the developer to run themselves.

### Branching

Trunk-based development — everything goes to `main`. No long-lived feature branches.
For short-lived work, branch names follow: `type/short-description` (e.g. `fix/login-crash`).
Claude never switches branches.

---

## 6. Changelog (Keep a Changelog format)

**Only update `CHANGELOG.md` when the user explicitly requests it** (e.g. "update the changelog", "do the release steps"). When updating, follow [keepachangelog.com](https://keepachangelog.com) format:

```markdown
## [A.B.C.D] - YYYY-MM-DD

### Added
- ...

### Changed
- ...

### Fixed
- ...

### Removed
- ...

### Security
- ...

### Tests (for .1 releases only)
- ...

### Results (for .1 releases only)
- <N> tests across <M> suites — all passed (<time>, <platform>).
```

Rules:
- Unreleased changes live under `## [Unreleased]` until a version is cut.
- `.1` releases use `### Tests` and `### Results` sections only.
- Never delete old entries — prepend new versions at the top.
- Dates use ISO 8601 (YYYY-MM-DD).
- **Never mention routine version string updates in CHANGELOG entries.** Bumping a version badge, label, or string in any file (CMakeLists.txt, README badge, CITATION.cff, banner.cpp, etc.) is mechanical and implied by the release — it adds no information. Only document substantive changes: new content, new features, new files, fixes. Example of what NOT to write: `` `README.md` — version badge updated to R.1.7.0 ``. Example of what IS fine: `` `README.md` — R.1.7.0 release row added; `docs/api/qudit-simulators.md` added to API Reference table ``.

---

## 7. Code Review Checklist

When reviewing code (own or user-provided), check:

- [ ] SOLID principles respected (especially SRP and OCP)
- [ ] No duplication (DRY)
- [ ] No magic numbers or unexplained constants
- [ ] Functions are small and single-purpose
- [ ] Naming follows language conventions
- [ ] Error handling is explicit and intentional — no silent failures, no bare `except`, no swallowed errors
- [ ] No dead code
- [ ] Tests exist for new logic (or `.1` release is planned)
- [ ] CHANGELOG updated (only if user requested a release)
- [ ] Commit message follows Conventional Commits
- [ ] Version bump is correct per §3 (only if user requested a version bump)

---

## 8. PR / Code Review Workflow

When the developer asks Claude to review a PR or a diff:

1. **Summarise the change** in 2–3 sentences — what it does, not how.
2. **Run through the checklist in §7** — call out any failures explicitly.
3. **Group feedback by severity:**
   - 🔴 **Must fix** — correctness, security, broken error handling
   - 🟡 **Should fix** — convention violations, DRY issues, unclear naming
   - 🟢 **Nice to have** — style, minor readability, suggestions
4. **End with a verdict:** Approve / Approve with minor comments / Request changes.

When reviewing Claude's own output, apply the same checklist before presenting code.

---

## 9. End-of-Task Output (Every Coding Task)

After every coding task — no matter how small — Claude must:

### 1. Update `changes_version.md`

Maintain a file called `changes_version.md` at the repo root. It is **always gitignored** (add it if not present).

> **NEVER overwrite `changes_version.md` using the Write tool.** This file is the developer's working memory — it accumulates all uncommitted changes across multiple sessions and conversations. If you overwrite it, you permanently destroy history that cannot be recovered without `git diff`. Always **Read the file first**, then **Edit** to append or modify specific sections. The file often contains entries from before the current conversation that you have no other way of knowing about.

The header is always `[PREV VERSION]` — the next version number is not known at the time of editing, so never assume it. Only the files changed **since the last push** appear in this file. If context is lost or a new chat starts mid-version, read this file to recover exactly what has changed without running `git diff`.

#### Hybrid format — minimal required sections, optional narrative sections

**Required sections** (always present, even for trivial changes):

```markdown
# [PREV VERSION] <stage> — changes in progress toward <next, if known>

## Files changed
<one line per modified file, grouped by subsystem, with a short what-changed>
- src/foo/bar.cpp — <what>
- tests/test_bar.cpp — <what>

## Status
READY TO BUMP | IN PROGRESS | BLOCKED — <one-line reason if not READY>

## Pending
<one line per remaining concrete step before the next push; "—" if nothing>
- Run full suite to verify
- Wait for review on #N
```

**Optional sections** — add only when the work warrants them, and remove them once they no longer apply. Don't pad trivial changes with empty sections.

- **`## What landed since [PREV]`** — when this in-progress version incorporates a merged PR, a hand-applied commit from elsewhere, or any change that's already in the git history but hasn't been versioned. Note what the merged work claimed vs. what actually held up under post-merge verification.
- **`## Diagnosis`** — when active debugging spans multiple iterations and you need to preserve concrete findings between sessions. Include: failing test output, the hypothesis being tested, what each fix attempt produced, the eventual root cause. The point is that if the conversation context is lost, the next session can pick up exactly where the last left off.
- **`## Decisions`** — when this in-progress work declares a convention, invariant, or architectural rule in `CLAUDE.md` / `docs/`. One sentence per decision + a link to where it now lives in the codebase. This is the audit trail for "why does the project work this way."
- **`## Bundled fixes`** — when this in-progress version bundles work beyond the original scope (e.g. you fixed three issues in one release because they were all in the same subsystem). One bullet per bundled item with its issue/PR reference and a one-line rationale for the bundling.

**When to escalate from minimal → with optional sections:** as soon as the work crosses a session boundary unfinished, or as soon as you can't recover state from just the file list. The narrative sections are there so a fresh chat picks up the thread without you having to re-explain.

**Example — trivial change (just the required sections):**

```markdown
# [PREV VERSION] R.1.10.5 — changes in progress

## Files changed
- src/foo/bar.cpp — fixed off-by-one in loop bound

## Status
READY TO BUMP

## Pending
- —
```

**Example — complex multi-session work (all sections in use):**

```markdown
# [PREV VERSION] R.1.10.4 — changes in progress toward R.1.10.5

## Files changed
### Convention + docs
- CLAUDE.md — added Project Conventions section (LSB-at-qubit-0)
- docs/Architecture.md — Qubit Ordering Convention reference + worked example
### Algorithm fixes
- src/algorithms/qft.cpp — moved SWAPs to deliver LSB-LSB QFT/IQFT
- src/algorithms/shor.cpp — iterate-counts strategy in find_order
### Tests
- tests/test_qft_convention.cpp — new 16-test convention regression suite

## Status
IN PROGRESS — diagnostic suite passes, awaiting full-suite verification

## Pending
- Run full ctest suite to confirm no unrelated breakage
- If green, execute version-bump checklist for R.1.10.5

## What landed since [PREV]
### Merged: PR #7 — fix(shor): correct bit extraction (commit edca71f)
Slice-fix part holds up; endianness claim turned out wrong (see Diagnosis).

## Diagnosis
First fix attempt (LSB-first within m, per PR #7) failed 0/20. Second attempt
(MSB-first) also failed 0/20. Both fail identically → assumption underlying
both is wrong, not a one-line endianness flip. Diagnostic test pinned it
down: QFT::build_circuit was internally qubit-0=MSB; the SWAPs landed on
the wrong side. Moving SWAPs to input-side of forward, output-side of
inverse delivers true LSB-LSB. Verified at n=2,3,4,5,11 (exact and
sinc-spread phases).

## Decisions
- LSB-at-qubit-0 declared project-wide convention (CLAUDE.md §"Project Conventions", docs/Architecture.md §"Qubit Ordering Convention"). Rule: new algorithms must include a non-symmetric end-to-end test, since phase=0 / uniform / palindromic tests mask convention bugs.

## Bundled fixes
- Issue #1 (NoiseModel after_gate silently forced true) — single-line API parameter; aligns with golden rule #1 (no silent failures).
- Issue #3 (thread_local RNG reseeded identically across OMP threads) — small, in the same correctness-sweep theme.
```

### 2. Provide the commit message (when user is ready to commit)

When the user signals they are ready to commit (e.g. "give me the commit message", "ready to commit", "commit this"), present the full commit message ready to copy-paste, derived from `changes_version.md`:

```
<type>(<scope>): <description>

[optional body]
```

Following Conventional Commits format (§5). Developer runs it themselves — Claude never runs `git commit`.

### 3. Clear `changes_version.md` after a push

When the user pushes to git, remind them to clear the contents of `changes_version.md` (keep the file, empty the contents) so it's ready for the next version:

> "Don't forget to clear `changes_version.md` now that this version is pushed."

---

## 10. Subagentic Development

When the user asks to use subagents, invoke `superpowers:subagent-driven-development` for the process. Apply these principles on top of it:

### Context efficiency is the entire point

**The coordinator (main Claude) must NOT investigate the problem before dispatching.** If the user says "there are 4 bugs", do not read files, trace code, or analyze the issue yourself — that is exactly the work the subagent should do. Doing it first wastes context and defeats the purpose.

The coordinator's only job is:
1. Distill what the user said into a focused brief
2. Tell the subagent where to look and what outcome is expected
3. Dispatch and review the result

### Subagent prompts must be lean

Do NOT give subagents complete instructions or pre-analyzed context. Instead:
- Give the task brief (distilled from the user's message, not from your own file reading)
- Tell them to read `CLAUDE.md` and follow `dev-workflow` — they must orient themselves
- Tell them the scope: which files/subsystems are relevant (from what the user told you, not from your own investigation)
- State the expected output clearly (report findings, fix and commit, etc.)

**Wrong:** Reading 5 files yourself, summarising the bug, then telling the subagent exactly what the issue is and where.

**Right:** "The user reports 4 bugs in the noise model. Read `CLAUDE.md`, follow dev-workflow, investigate `src/noise/` and `tests/test_noise.cpp`, and report what you find with proposed fixes."

### Each subagent is self-directing

Subagents have full tool access. Trust them to read the codebase, find the issue, and report. Your job is to give them a sharp brief and good scope, not to pre-chew the problem.

### Review, don't re-investigate

When a subagent reports back, review its findings and decide next steps. Do not re-read the same files the subagent already read — trust the report. If the report is unclear, ask the subagent to clarify (re-dispatch), not investigate yourself.

---

## 11. Workflow Summary

```
[Major change flagged by developer]
  → Brainstorm Sprint (§1a): ask 2–3 questions → summarise → confirm
  → Full plan: restate goal, list files, test trigger → approve
  → Code (§2) → Tests if .1 triggered (§4)
  → Update changes_version.md (§9)
  → CHANGELOG (§6) only if user explicitly requests it

[Significant change, not flagged major]
  → Brief plan: goal, files → nod → code
  → Update changes_version.md (§9)

[Minor change]
  → Ask if anything unclear → one-line confirmation → code
  → Update changes_version.md (§9)

[User ready to commit]
  → Present commit message derived from changes_version.md (§9)

[User pushes to git]
  → Remind user to clear contents of changes_version.md (§9)

[Code review / PR]
  → Summarise → checklist → grouped feedback → verdict (§8)
```

---

## Reference files

- `references/versioning-examples.md` — Extended versioning examples and edge cases
- `references/conventional-commits.md` — Full list of commit types and examples
