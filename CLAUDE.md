# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Groovy scripts that build a personal knowledge management (PKM) system on top of Freeplane, distributed as a Freeplane add-on (`*.addon.mm`).

## Commands

```bash
gradle test                      # all tests
gradle test --tests UtilsSpec    # a single test class
gradle check                     # tests + codenarc + tools/check-conventions.sh
gradle packageAddon              # produces build/addon/freeplane-pkm-<version>.addon.mm
gradle packageAddon -PaddonVersion=v1.0.0
```

Java 17 is pinned; keep `source/targetCompatibility` in `build.gradle` in sync with `java-version` in `.github/workflows/package-addon.yml`. Releases are cut by pushing a `v*` tag.

## Structure

- `scripts/*.groovy` — one script per menu entry, written as top-level statements against the bindings Freeplane injects: `node`, `c`, `ui`. Anything touching the outside world (`Desktop`, processes) goes through `lib/Utils.groovy` so specs can replace it.
- `lib/Utils.groovy` — document-directory conventions (page/assets paths, BOM handling, node ↔ file mapping) and the Next Steps refresh. `lib/SearchIndex.groovy` — the Lucene full-text index (PDFBox/POI for content extraction, Kuromoji for Japanese). `lib/` is compiled with its dependencies into one jar (`shadowJar`) that goes on the add-on's classpath; Groovy itself is excluded since Freeplane provides it.
- `test/*.groovy` — Spock. Script specs extend `ScriptSpec`, which evaluates the real script file in a `GroovyShell` with fake `node`/`c`/`ui` (see its class doc). Node fakes are `FakeNode`, never a cyclic `Expando` (its `toString()` overflows the stack inside Gradle's JUnit listener, which then records the failing test as *skipped*; `gradle test` fails on any skipped test for that reason).
- `gradle/packageAddon.gradle` — builds the add-on; `addonScriptDefs` holds menu titles, shortcuts and permissions. **Adding or removing a script means updating `addonScriptDefs`, adding `test/<Name>Spec.groovy` (or a reasoned entry in `tools/spec-exempt.txt`), and the README shortcut table** — `tools/check-conventions.sh` enforces the first two.

## Mind map conventions (not discoverable from the code alone)

- The document directory is read from `root > config > docDirPath > <path>`.
- Page node: text links `<docDir>/<text>.md`; directory node: `<docDir>/<text>/`; attachments live in `<text>.assets/`. Pages are UTF-8 with a BOM (`Utils.readPage`/`writePage`).
- Up to 3 list items under a page's `### Next Steps` heading are synced into the node's children; a page whose children already match is left untouched. The same refresh deletes `root > ToDo` items (`AddToToDo`'s `<item> (<page>)`, linking the page file) whose text is no longer among *all* the page's Next Steps items, or whose page is gone.
- `root > ToDo` holds the task list. Anything under a node named `archive` is hidden by the default filter. Dates use `yy/MM/dd`.
- Node mutations happen on the Swing EDT (`SwingUtilities.invokeLater`); file I/O is kept off it.
- `scripts/init/init.groovy` runs at startup and calls `Utils.updateNextStepsAndIndex` on map changes every few minutes. Init scripts run with the *global* scripting permissions, so it goes through `ScriptingEngine.executeScript` with read/write permission granted explicitly.
- Every menu-script invocation runs in its own class loader over the lib jar, so nothing survives between invocations. `Search.groovy` therefore hides its dialog on close and re-shows it on the next F6 (a second dialog would reload Lucene and Kuromoji's dictionary while the first stays alive), and `SearchIndex.warmUp()` loads the dictionary during the dialog's background refresh.
- The search index lives at `<docDir>/.search-index/` (Lucene index plus `files.meta`: the schema version and each indexed file's mtime, for incremental updates). Bump `SearchIndex.INDEX_SCHEMA_VERSION` when the analyzer or fields change incompatibly; `updateIndex()` then rebuilds from scratch. `SearchIndexSpec` pins the current tokenization, so forgetting the bump fails the build.

## Definition of done

A change touching `scripts/`, `lib/`, or `test/` is finished when `gradle check` passes. The pre-commit hook in `.claude/settings.json` (`tools/hooks/pre-commit-gate.sh`) enforces this on `git commit`.
