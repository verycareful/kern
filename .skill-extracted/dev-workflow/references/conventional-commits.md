# Conventional Commits Reference

## Format

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

- **type**: required
- **scope**: optional, noun in parentheses (e.g. `auth`, `api`, `ui`)
- **description**: short imperative sentence, lowercase, no period
- **body**: free-form, explains *what* and *why*
- **footer**: `BREAKING CHANGE: <description>` or issue refs like `Fixes #123`

---

## Types

| Type       | Use for                                              | Bumps D? |
|------------|------------------------------------------------------|----------|
| `feat`     | New feature                                          | → bumps C |
| `fix`      | Bug fix                                              | ✓ D      |
| `docs`     | Documentation only                                   | ✓ D      |
| `style`    | Formatting, whitespace (no logic change)             | ✓ D      |
| `refactor` | Code restructure (no feature, no fix)                | ✓ D      |
| `perf`     | Performance improvement                              | ✓ D      |
| `test`     | Adding or fixing tests                               | ✓ D      |
| `chore`    | Build process, tooling, dependency updates           | ✓ D      |
| `ci`       | CI/CD config changes                                 | ✓ D      |
| `revert`   | Reverts a previous commit                            | ✓ D      |

Breaking changes: append `!` to any type or add `BREAKING CHANGE:` footer → bumps B.

---

## Examples

```
feat(auth): add OAuth2 login flow

fix(api): handle null response from payment gateway

chore(deps): upgrade lodash to 4.17.21

feat!: remove support for Node 16

BREAKING CHANGE: Node 16 is no longer supported. Upgrade to Node 18+.

docs(readme): add versioning scheme explanation

refactor(parser): extract token validation into separate function

test(auth): add test suite for OAuth2 flow (for .a release)
```

---

## Multi-line body example

```
fix(cache): prevent race condition on concurrent writes

Previously, two simultaneous writes could corrupt the cache index
by both reading the stale version before either had committed.

Added a mutex lock around the write transaction.

Fixes #441
```

---

## Claude's commit message workflow

1. After code is ready, Claude composes the commit message.
2. Presents it clearly to the developer.
3. Developer copies and runs `git commit -m "..."` themselves.
4. Claude never runs `git commit`.
