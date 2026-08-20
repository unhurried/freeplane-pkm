# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Groovy scripts that build a personal knowledge management (PKM) system on top of Freeplane, distributed as a Freeplane add-on (`*.addon.mm`).

## Commands

```bash
gradle test                      # all tests
gradle test --tests UtilsSpec    # a single test class
gradle check                     # tests + script syntax check + convention/shell checks (tools/*.sh)
gradle build                     # compile + check
gradle packageAddon              # produces build/addon/freeplane-pkm-<version>.addon.mm
gradle packageAddon -PaddonVersion=v1.0.0
```

Java 17 is pinned; keep `source/targetCompatibility` in `build.gradle` in sync with `java-version` in `.github/workflows/package-addon.yml`. Releases are cut by pushing a `v*` tag.

## Structure

- `scripts/*.groovy` — one script per menu entry. Written as top-level statements (not classes), relying on the bindings Freeplane injects: `node`, `c`, `ui`.
- `lib/Utils.groovy` — shared node/doc-directory logic. `lib/SearchIndex.groovy` (+ `lib/SearchHit.groovy`) — full-text search: builds/queries a Lucene index over the document directory, including content extraction for PDF (PDFBox) and current-format Office documents (Apache POI); Japanese content/filenames are tokenized with Lucene's Kuromoji morphological analyzer (`lucene-analysis-kuromoji`, bundled IPADIC dictionary), not a bigram fallback; see the `.search-index` convention below. `lib/*.groovy` is compiled, together with its third-party dependencies (Lucene/PDFBox/POI, via the `shadowJar` task in `build.gradle`), into a jar that goes on the add-on's classpath; raw `.groovy` sources placed under the add-on's `lib` node are not compiled and would fail to resolve. Groovy itself is deliberately excluded from that jar (`compileOnly` in `build.gradle`) since Freeplane already provides it at runtime.
- `test/*.groovy` — Spock. `UtilsSpec` mocks Freeplane's node API with `Expando`; `SearchIndexSpec` runs against a real (temp-dir) Lucene index and generates its own PDF/docx fixtures via PDFBox/POI. The per-script specs (`EditSpec`, `NewPageSpec`, …) extend `ScriptSpec`, which evaluates the real `scripts/*.groovy` file in a `GroovyShell` with `node`/`c`/`ui` faked — see the testing policy below.
- `gradle/packageAddon.gradle` — builds the add-on. **When adding or removing a script in `scripts/`, update `addonScriptDefs` too** (the build fails on a mismatch). Menu titles, execution modes, shortcuts, and per-script permissions are defined there.

## Testing policy

- Everything in `lib/` is tested, and so is every script that carries logic of its own. Out of scope: scripts that only delegate to Freeplane internals (`FoldOneLevel`, `UnfoldOneLevel`, `scripts/init/init.groovy`) — they `import org.freeplane.*`, which isn't obtainable as a dependency — and `Search.groovy`, which only wires up a Swing `JDialog` (`HeadlessException` in the test JVM); the logic it calls into lives in and is tested via `Utils`/`SearchIndex`. **Adding a script means adding its spec.** Both categories of exception are enforced, not just documented: `tools/check-conventions.sh` requires every `scripts/*.groovy` *and* every `lib/*.groovy` file to have either a `test/<Name>Spec.groovy` or a reasoned entry in `tools/spec-exempt.txt` (entries are repo-relative paths, e.g. `scripts/Search.groovy`, `lib/SearchHit.groovy`) — see the `add-script` skill for the full checklist when adding/removing/renaming a script.
- Script specs extend `test/ScriptSpec.groovy`, which runs the real script file through a `GroovyShell` with fake `node`/`c`/`ui` bindings. Dialog answers are scripted (`inputAnswers`, `confirmAnswer`), and what the script did is recorded (`errorMessages`, `openedInDesktop`, `selectedNodes`, `filterCalls`).
- Extend `FakeNode` (not an `Expando`) when a script starts using more of Freeplane's node API. Node fakes form a parent/child cycle and `Expando.toString()` recurses into it, so any failure message mentioning one overflows the stack, kills Gradle's JUnit listener mid-report, and gets recorded as a *skipped* test in a green build. `gradle test` therefore fails if any test is skipped, and `check-conventions.sh` rejects `new Expando(` in any spec other than `ScriptSpec`/`UtilsSpec`, where the fixtures are acyclic.
- Side effects on the outside world belong in `lib/Utils.groovy` (e.g. `Utils.openInDesktop`) rather than inline in a script, so specs can replace them: `java.awt.Desktop` is unusable in a headless test JVM.
- Freeplane runs the add-on on a full Groovy distribution, so scripts may use its extension modules; the ones they rely on (`groovy-dateutil`, `groovy-datetime`) are test-only dependencies in `build.gradle`.

## Mind map conventions (not discoverable from the code alone)

- The document directory is read from a config node in the map: `root > config > docDirPath > <path>`.
- Page node: its text links to `<docDir>/<text>.md`. Directory node: links to `<docDir>/<text>/`. Attachments live in `<text>.assets/`.
- List items under a page's `### Next Steps` heading (up to 3) are synced into the node's children. The last run time is stored in `root > config > nextStepsUpdatedAt` and used to skip unmodified pages.
- `root > ToDo` holds the task list. Anything under a node named `archive` is hidden by the default filter. Dates use `yy/MM/dd`.
- Node mutations must happen on the Swing EDT (`SwingUtilities.invokeLater`); file I/O is moved off it.
- `scripts/init/init.groovy` runs at Freeplane startup and refreshes Next Steps and the search index on map changes. Init scripts run with the *global* scripting permissions, not the add-on's per-script ones, so it invokes the update through `ScriptingEngine.executeScript` with read and write permission granted explicitly.
- The full-text search index lives at `<docDir>/.search-index/` (a Lucene index plus a small file tracking each indexed file's last-modified time, for incremental updates). `SearchIndex.updateIndex()` skips its own directory (and any other dot-directory) when scanning, and `FindUnlinked.groovy` explicitly excludes it too, so it's never reported as an unlinked directory. A schema version is also stored there (`index.version`); bump `SearchIndex.INDEX_SCHEMA_VERSION` whenever the analyzer or field configuration changes in a way that makes old and new tokens incompatible, and `updateIndex()` transparently does a full rebuild (dropping and re-extracting every file) on the next run. Forgetting that bump is invisible in the build (the tests always index from scratch), so the schema-affecting code is delimited by `// schema-region:begin`/`:end` comments and its hash pinned in `tools/index-schema.fingerprint`: change anything inside those markers and `gradle check` fails until you bump the version and run `bash tools/check-conventions.sh --update-index-fingerprint`.

## Definition of done

A change touching `scripts/`, `lib/`, or `test/` isn't finished until `gradle check` passes. That single command covers everything above:

- the Spock suite (`test/*.groovy`)
- `test/ScriptSyntaxSpec.groovy`, which parses every `scripts/*.groovy` file to catch syntax errors that would otherwise ship unnoticed (`gradle/packageAddon.gradle` embeds scripts as plain text, never compiling them)
- `tools/check-conventions.sh` (wired in as the `checkConventions` task), which is the machine-checkable half of this file: `scripts/`+`lib/`↔spec sync, script↔`addonScriptDefs` sync, no outside-world side effects (`Desktop`, `ProcessBuilder`, `Runtime`, `System.exit`, `.execute()`) called directly from `scripts/`, no silently skipped tests, no `Expando` node fixtures outside `ScriptSpec`/`UtilsSpec`, unique and well-formed menu shortcuts/titles, `README.md`'s shortcut table ↔ `addonScriptDefs`, `addonZipEntries` covering everything under `scripts/` that isn't a menu script, the `SearchIndex` schema fingerprint, and the Java-version sync mentioned above
- `tools/check-shell.sh` (the `checkShell` task), which runs shellcheck over `tools/*.sh` and `tools/hooks/*.sh` — the guardrail scripts themselves, which fail open when broken. It passes with a notice when shellcheck isn't installed locally; CI always has it

Its failures are not advisory — a violation it reports is a real gap, not a style nit to weigh. If a file genuinely can't have a spec, say so in `tools/spec-exempt.txt` with a reason rather than leaving it silently uncovered (see the `add-script` skill). Two generated artifacts are refreshed with a flag rather than by hand: `--update-readme-shortcuts` (README's shortcut table) and `--update-index-fingerprint` (the search-index schema fingerprint); there is deliberately no blanket `--fix`.

The same checks run automatically from three Claude Code hooks, whose bodies live in `tools/hooks/` and are wired up in `.claude/settings.json`:

- `post-edit-check.sh` (`PostToolUse`) — runs `check-conventions.sh` right after an edit to `scripts/`, `lib/`, `test/`, or `gradle/packageAddon.gradle`
- `stop-check.sh` (`Stop`) — runs it once more as a turn ends, and blocks on remaining violations. This is the backstop for changes made by shell commands (`sed -i`, `mv`, `git apply`), which no `PostToolUse` matcher ever sees
- `pre-commit-gate.sh` (`PreToolUse`) — denies a `git commit` whose tree doesn't pass `gradle check`
