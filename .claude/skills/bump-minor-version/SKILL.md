---
name: bump-minor-version
description: Use when asked to bump, raise, or release the next minor version of the Freeplane PKM add-on (e.g. 0.21.0 → 0.22.0), or to cut a new release / version up.
---

# Bumping the minor version

The version lives in one place: `version=` in `gradle.properties` (the build and
the default add-on version both read it). A bump is a single-line commit, tagged.

1. **Start clean, on `main`.** `git status` must show no changes; a bump commit
   contains only `gradle.properties`.
2. **Compute the next version.** Read `version=X.Y.Z`; the new one is
   `X.(Y+1).0` (patch resets to 0). Cross-check with
   `git tag --sort=-creatordate | head -1` — the latest tag should be `vX.Y.Z`.
   If they disagree, stop and ask.
3. **Edit only the `version=` line** in `gradle.properties`. Leave the comments
   and other properties alone.
4. **Commit** with exactly `Bump version to X.(Y+1).0` (see the `commit-message`
   skill: one line, no trailer). The pre-commit hook runs `gradle check`; let it.
5. **Tag** the bump commit with a lightweight tag: `git tag vX.(Y+1).0`
   (existing tags are lightweight and point at their bump commits).
6. **Pushing is a release.** `git push origin main vX.(Y+1).0` triggers
   `.github/workflows/package-addon.yml`, which publishes the add-on. Ask the
   user before pushing; never push without confirmation.

## Quick reference

| Before | After | Commit message | Tag |
|---|---|---|---|
| `version=0.21.0` | `version=0.22.0` | `Bump version to 0.22.0` | `v0.22.0` |

## Common mistakes

- Bumping the patch (`0.21.1`) or keeping it (`0.22.1`) — a minor bump always ends in `.0`.
- Tag without the `v` prefix — CI only triggers on `v*`.
- Mixing feature changes into the bump commit.
- Tagging before the commit succeeds (hook failure leaves the tag on the previous commit).
