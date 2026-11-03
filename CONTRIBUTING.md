# Contributing to Kern

Bug reports, fixes, features, tests and documentation are all welcome. This file
covers how to report a problem, how to send a change, and the rules a change has to
meet before it is merged.

## Licence

Kern is licensed under AGPL-3.0, and contributions are accepted under the same
licence: inbound equals outbound. By opening a pull request you agree that your
change is released under AGPL-3.0 as part of Kern, and you confirm that you have
the right to submit it. You keep the copyright on what you wrote.

Code copied from another project has to come under a licence compatible with
AGPL-3.0, and the pull request has to say where it came from. A new bundled file
(a font, a script, a library) also needs a line in `licenses/index.tsv`, so that it
appears under Settings > Notices.

## Reporting a bug

Open an issue with the **Bug report** form. The form asks for:

- **Kern version**, from Settings > About > Version (for example `0.1.14.1`).
- **Android version and device.**
- **File type**, and **how the file was opened**: from Kern's own file browser, with
  "Open with" from another app (name the app: Gmail, Files, Drive), or through the
  system file picker. Many bugs depend on this, because each route hands Kern the
  file differently.
- **Steps to reproduce**, starting from a fresh launch of Kern.
- **What you expected** and **what happened**, with the exact text of any error.

Attach a screenshot or the file itself when you can share it. If the file is private,
say so and describe it instead (its size, how it was made, what it contains).

One problem per issue. Write the title as the symptom and where it shows, for
example `Word: bold text loses its formatting after Replace all`. Put the version in
the body, not the title.

An issue that has too little to act on gets the `Needs info` label and a question.
It may be closed if the details do not follow; reopening it with them is welcome.

Security problems are different: do not open a public issue. See
[SECURITY.md](SECURITY.md).

## Requesting a feature

Open an issue with the **Feature request** form. Start from the problem: what you
were trying to do, and where Kern stopped you. A proposed design is optional.
`PDF: dark mode` cannot be acted on; `PDF: reading at night, white pages are too
bright` can, because it says what the change is for.

## Labels

Maintainers set labels when triaging. Suggesting them in a report is welcome.

- **Type**: `bug`, `enhancement`, `documentation`, `performance`, `question`.
- **Area**: `csv`, `excel`, `word`, `pptx`, `pdf`, `epub`, `markdown`, `text`,
  `browser` (the file browser and opening files from other apps), `settings`.
- **Severity**, on bugs only, exactly one:
  - `critical`: a file is corrupted or lost on save, or Kern crashes on a main path
    (opening, editing or saving a supported file).
  - `high`: a main feature does not work and there is no reasonable workaround.
  - `medium`: a clear failure with a workaround.
  - `low`: cosmetic or minor.

## Sending a change

1. Fork the repository and branch from `main`. Name the branch for the change:
   `fix/word-replace-formatting`, `feat/csv-sort`.
2. A bug fix can go straight to a pull request. A feature, or any change to how an
   editor behaves, needs an issue first, so that the design is agreed before the work
   starts.
3. Make the change, with tests where it touches format handling (see below).
4. Run the unit tests and lint, and make sure both pass:
   `gradle testDebugUnitTest lintDebug` with Gradle 8.10, or the same tasks from
   Android Studio. CI runs these on every pull request.
5. Open the pull request against `main`. Describe what changed and why, and link the
   issue.

Do not change the version. Version strings, `CHANGELOG.md` and releases are handled
by the maintainer when the change is merged.

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/):
`type(scope): summary`, with the scope naming the area.

```
fix(word): keep bold and italic through Replace all
feat(csv): sort by a column
test(excel): formula cells across inserted rows
docs(architecture): describe the PDF text layer
```

### Know what you submit

Read every line of your change before you submit it, and be able to explain why each
line is there. Whatever produced the code (you, a generator, an AI tool, another
project), you are the one submitting it, and review questions go to you. A pull
request whose author cannot answer for its contents is closed.

## Rules for code

These hold everywhere in Kern. A change that breaks one is not merged.

- **No network.** Kern has zero network permissions and makes no network requests:
  no analytics, telemetry, crash reporting, font or script loading from the web.
  Everything works offline. A web link in a document opens in another app, and only
  after the user confirms.
- **Files are edited in place.** Kern reads and writes a document where it lives and
  keeps no app folder of copies.
- **No `java.awt` on Android.** Android has no `java.awt.geom`. An Apache POI call
  that returns or builds a `Rectangle2D`, `Dimension` or `Point2D` (for example
  `XSLFShape.getAnchor()`) compiles and then crashes on the device. Read geometry
  from the XML beans instead, as the PowerPoint editor does.
- **PDF bridge changes are discussed first.** `src/pdf-bridge/` calls into a native
  library shared with another project. Open an issue before changing it.
- **Tests come with format handling.** New or changed code that reads, writes or
  edits a document format comes with unit tests. Test files mirror the source:
  `src/editors/csv/CsvParser.kt` is tested in `tests/editors/csv/CsvParserTest.kt`.
- **Comments explain the code as it stands**: how it works, why it is there, what
  breaks without it. What changed, when, and why it changed belong in the commit
  message and the pull request, not in a comment.
- **No em dashes and no double hyphens** in code, comments, documentation, commit
  messages or pull request text. Use a colon, a comma, parentheses or two sentences.
- Follow the Kotlin conventions: `PascalCase` classes, `camelCase` functions and
  variables, packages under `dev.kern`.

## Good first issues

Issues labelled `good first issue` are small and self-contained. Comment on one
before starting, so that two people do not work on it at once.
