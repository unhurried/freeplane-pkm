# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Groovy scripts that build a personal knowledge management (PKM) system on top of Freeplane, distributed as a Freeplane add-on (`*.addon.mm`).

## Commands

```bash
gradle test                      # all tests
gradle test --tests UtilsSpec    # a single test class
gradle build                     # compile + test
gradle packageAddon              # produces build/addon/freeplane-pkm-<version>.addon.mm
gradle packageAddon -PaddonVersion=v1.0.0
```

Java 17 is pinned; keep `source/targetCompatibility` in `build.gradle` in sync with `java-version` in `.github/workflows/package-addon.yml`. Releases are cut by pushing a `v*` tag.

## Structure

- `scripts/*.groovy` — one script per menu entry. Written as top-level statements (not classes), relying on the bindings Freeplane injects: `node`, `c`, `ui`.
- `lib/Utils.groovy` — shared node/doc-directory logic. `lib/SearchIndex.groovy` (+ `lib/SearchHit.groovy`) — full-text search: builds/queries a Lucene index over the document directory, including content extraction for PDF (PDFBox) and current-format Office documents (Apache POI); see the `.search-index` convention below. These are the only tested code (`scripts/` has no tests). `lib/*.groovy` is compiled, together with its third-party dependencies (Lucene/PDFBox/POI, via the `shadowJar` task in `build.gradle`), into a jar that goes on the add-on's classpath; raw `.groovy` sources placed under the add-on's `lib` node are not compiled and would fail to resolve. Groovy itself is deliberately excluded from that jar (`compileOnly` in `build.gradle`) since Freeplane already provides it at runtime.
- `test/UtilsSpec.groovy`, `test/SearchIndexSpec.groovy` — Spock. `UtilsSpec` mocks Freeplane's node API with `Expando`; `SearchIndexSpec` runs against a real (temp-dir) Lucene index and generates its own PDF/docx fixtures via PDFBox/POI.
- `gradle/packageAddon.gradle` — builds the add-on. **When adding or removing a script in `scripts/`, update `addonScriptDefs` too** (the build fails on a mismatch). Menu titles, execution modes, shortcuts, and per-script permissions are defined there.
- `accelerator.properties` — shortcuts for manual installation. Add-on shortcuts live in `addonScriptDefs`, so keep both in sync.

## Mind map conventions (not discoverable from the code alone)

- The document directory is read from a config node in the map: `root > config > docDirPath > <path>`.
- Page node: its text links to `<docDir>/<text>.md`. Directory node: links to `<docDir>/<text>/`. Attachments live in `<text>.assets/`.
- List items under a page's `### Next Steps` heading (up to 3) are synced into the node's children. The last run time is stored in `root > config > nextStepsUpdatedAt` and used to skip unmodified pages.
- `root > ToDo` holds the task list. Anything under a node named `archive` is hidden by the default filter. Dates use `yy/MM/dd`.
- Node mutations must happen on the Swing EDT (`SwingUtilities.invokeLater`); file I/O is moved off it.
- `scripts/init/init.groovy` runs at Freeplane startup and refreshes Next Steps and the search index on map changes. Init scripts run with the *global* scripting permissions, not the add-on's per-script ones, so it invokes the update through `ScriptingEngine.executeScript` with read and write permission granted explicitly.
- The full-text search index lives at `<docDir>/.search-index/` (a Lucene index plus a small file tracking each indexed file's last-modified time, for incremental updates). `SearchIndex.updateIndex()` skips its own directory (and any other dot-directory) when scanning, and `FindUnlinked.groovy` explicitly excludes it too, so it's never reported as an unlinked directory.
