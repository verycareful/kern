# Contributing to Kern

Thanks for your interest. Kern is open-source (AGPL-3.0) and welcomes contributors of all levels.

## Ground rules

- All contributions must be compatible with AGPL-3.0
- No network permissions, no telemetry, no analytics - ever
- Every feature must work fully offline
- Test coverage is required for all new format handling code

## Branching

- `main` - stable, always buildable
- `dev` - integration branch for in-progress work
- Feature branches: `feat/csv-editor`, `fix/pdf-crash-on-open`, etc.

## Commit format

Conventional Commits:

```
feat(excel): add formula bar to cell editor
fix(pdf): crash when opening password-protected file
docs(architecture): add JNI bridge diagram
test(csv): add edge cases for empty rows
```

## Versioning

A.B.C.D scheme - see README. Do not bump versions yourself; maintainers handle releases.

## PDF bridge

`src/pdf-bridge/` holds the native PDF bridge (JNI/NDK). If you would like to contribute there, please open an issue first so we can discuss the boundary before you start.

## Good first issues

Look for the `good-first-issue` label on GitHub. Good starting points:
- Adding file type icons to the browser
- Improving error messages for corrupt files
- Writing instrumented tests for existing editors

## Pull requests

1. Branch from `dev`
2. Write tests
3. Run lint and tests (in Android Studio, or `gradle lint test` with a local Gradle 8.10+) - must be green
4. Open PR against `dev`, not `main`
5. Describe what changed and why
