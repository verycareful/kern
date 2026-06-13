# Versioning Examples & Edge Cases

## Standard progression for a new project

```
0.1.0.a   → First test suite (Alpha, Major 1, Minor 0)
0.1.0.b   → First real patch
0.1.0.c   → Second patch
0.1.1.a   → New feature landed, test suite required
0.1.1.b   → Patch on Minor 1
1.2.0.a   → Breaking change bumped Major to 2 (Beta), test suite
1.2.0.b   → First patch
R.1.0.a   → Promoted to Release, Major 1, Minor 0, test suite
R.1.0.b   → First patch on release
```

## D slot exhaustion (design smell)

```
R.2.3.a   test suite
R.2.3.b   patch
R.2.3.c   patch
...
R.2.3.z   ← STOP. Flag to developer.
            "You've hit 26 patches on Minor 3. This is a design smell.
             Recommend reverting C to the previous minor and reassessing."
```
Never auto-bump past z. Always stop and flag.

## A transitions (manual only)

```
0.x.x.x   → Alpha phase
1.x.x.x   → Beta phase   (developer decides when ready)
R.x.x.x   → Released     (developer decides when ready)
```
Claude never changes A. It may suggest ("this looks stable enough for Beta") but never acts.

## What triggers what

| Commit type       | Auto or Manual | Bumps | Resets        | Requires .a? |
|-------------------|---------------|-------|---------------|--------------|
| fix:              | Auto          | D     | nothing       | No           |
| docs:, chore:     | Auto          | D     | nothing       | No           |
| feat:             | Confirm first | C     | D → a         | Yes          |
| BREAKING CHANGE:  | Confirm first | B     | C → 0, D → a  | Yes          |
| A transition      | Manual only   | A     | nothing forced| Recommended  |

## Edge: what if there are multiple feat: commits before a release?

Only one C bump per release cut. If three `feat:` commits land before versioning, C bumps once and a single `.a` test suite covers all three features.

## Edge: first version of a new project

Start at `0.1.0.a` — the first thing you do is write a test suite, even before `0.1.0.b`.
