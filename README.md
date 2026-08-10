# Freeplane PKM

Freeplane PKM is a project for building a personal knowledge management (PKM) system on Freeplane, a mind-mapping tool.

### Key Features

- **Document Management**: Manage links between mind map nodes and Markdown files/directories
- **Full-Text Document Search**: Indexed, keyword search across your Markdown, PDF and Office document files - see [Full-Text Document Search](#full-text-document-search) below
- **Mind Map Search and Filtering**: Search and filter mind map nodes by conditions
- **Task Management**: Reflect tasks written in Markdown files into the mind map

## Installation

1. Download `freeplane-pkm-<version>.addon.mm` from the [releases page](https://github.com/unhurried/freeplane-pkm/releases).
2. In Freeplane, select Tools → Add-ons → Search and Install, choose the downloaded file, and install it.
3. Restart Freeplane.

The scripts are available under Tools → Scripts → Freeplane PKM, with keyboard shortcuts (F1–F10, F12) assigned automatically.

## Full-Text Document Search

Run Tools → Scripts → Freeplane PKM → Search (shortcut `F6`) to open a search dialog: type keywords, and matching files show up in a result list with a short content snippet. Double-click a result (or select it and press Enter) to open it with the OS default application, via `Desktop.open()`.

- **Formats**: Markdown/plain text, PDF, and current-format Office documents (`.docx`, `.xlsx`, `.pptx`). Any other file type is still searchable by file name. Legacy Office formats (`.doc`, `.xls`, `.ppt`) are not supported.
- **Matching**: multiple space-separated keywords are combined with AND, matched case-insensitively against both file content and file name.
- **Jumping to the map node**: the result list's Node column shows the mind map node (if any) that links a result's file, its containing directory, or - for a file under a page's `.assets/` directory - its page. Select it and press `Ctrl+Enter`, use the `Select Node` button, or right-click the row, to select and center that node in the map instead of opening the file.
- **Index**: kept at `<docDir>/.search-index/` (a Lucene index plus a small file recording each indexed file's last-modified time, used to skip unchanged files on the next update). It refreshes automatically every few minutes while a map is open, and whenever the Search dialog itself opens; run Tools → Scripts → Freeplane PKM → Update Next Steps and Search Index to refresh it on demand instead of waiting.
- This replaces the previous reliance on Windows Search / Inazuma Search - the index and search UI are entirely built into the add-on, so no external search tool needs to be installed.

## Development

### Requirements

- **Java**: JDK 17
- **Gradle**: 7.0 or later (can be installed automatically with the Gradle wrapper)
- **Freeplane**: Latest version (used as the script runtime environment)

### Build and Test

```bash
gradle build   # compile + test
gradle test    # tests only
```

### Packaging as a Freeplane Add-on

```bash
gradle packageAddon                        # uses addonDefaultVersion from gradle.properties
gradle packageAddon -PaddonVersion=v1.0.0  # explicit version
```

This generates `build/addon/freeplane-pkm-<version>.addon.mm` (and `version.properties`) without requiring a running Freeplane instance. The task replicates what the Freeplane devtools add-on does; see `gradle/packageAddon.gradle` for how scripts, the `lib/` classes (compiled together with their third-party dependencies into a fat jar - see `shadowJar` in `build.gradle`), and the template files are embedded. Because of those dependencies (Lucene, PDFBox, Apache POI, for full-text search), the packaged add-on is tens of megabytes; this is expected.

### Release via GitHub Actions

The `Package Add-on` workflow (`.github/workflows/package-addon.yml`) runs tests and packaging on every push/PR and uploads the add-on as a build artifact. Pushing a tag matching `v*` (e.g. `v1.0.0`) additionally creates a GitHub release with the `.addon.mm` file attached:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Related Links

- [Freeplane Official Site](https://freeplane.org/)
- [Groovy Official Site](https://groovy-lang.org/)
