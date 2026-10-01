# Freeplane PKM

Freeplane PKM is a project for building a personal knowledge management (PKM) system on Freeplane, a mind-mapping tool.

### Key Features

- **Document Management**: Manage links between mind map nodes and Markdown files/directories
- **Full-Text Document Search**: Indexed, keyword search across your Markdown, PDF and Office document files - see [Full-Text Document Search](#full-text-document-search) below
- **Mind Map Search and Filtering**: Search and filter mind map nodes by conditions
- **Task Management**: Reflect tasks written in Markdown files into the mind map; ToDo items whose task has been removed from its page are dropped automatically

## Installation

1. Download `freeplane-pkm-<version>.addon.mm` from the [releases page](https://github.com/unhurried/freeplane-pkm/releases).
2. In Freeplane, select Tools → Add-ons → Search and Install, choose the downloaded file, and install it.
3. Restart Freeplane.

The scripts are available under Tools → Scripts → Freeplane PKM, with the keyboard shortcuts below assigned automatically.

| Shortcut | Menu entry |
| --- | --- |
| F1 | Open |
| F2 | Edit |
| F3 | Fold One Level |
| F4 | Unfold One Level |
| F5 | Filter |
| F6 | Search |
| F7 | Toggle Checkmark |
| F8 | Add to ToDo |
| F9 | New Page |
| F10 | New Directory |
| F11 | Delete |
| F12 | Update Next Steps and Search Index |

With several nodes selected, Open, Delete and Add to ToDo act on all of them (Delete asks once, listing every target), Toggle Checkmark, New Page and New Directory run on each, and Edit and Add Assets ask for a single node to be selected.

## Full-Text Document Search

Run Tools → Scripts → Freeplane PKM → Search (shortcut `F6`) to open a search dialog: type keywords, and matching files show up in a result list with a short content snippet, the matched keywords highlighted. Double-click a result (or select it and press Enter) to open it with the OS default application, via `Desktop.open()`.

- **Formats**: Markdown/plain text, PDF, and current-format Office documents (`.docx`, `.xlsx`, `.pptx`). Any other file type is still searchable by file name. Legacy Office formats (`.doc`, `.xls`, `.ppt`) are not supported.
- **Matching**: multiple space-separated keywords are combined with AND, matched case-insensitively against both file content and file name.
- **Sorting**: the `Sort` box orders results by last-modified time, newest first (the default), or by relevance (file-name matches rank above content-only matches).
- **Filtering**: the `Show` box limits results to pages (the default), attachments, or both. A page is a `.md` file outside any `<page>.assets/` directory; every other file - including anything under an `.assets/` directory and non-Markdown files placed directly in the document directory - counts as an attachment.
- **Jumping to the map node**: a result's node is the mind map node (if any) that links its file, its containing directory, or - for a file under a page's `.assets/` directory - its page. Select the result and press `Ctrl+Enter`, use the `Select Node` button, or right-click the row, to select and center that node in the map instead of opening the file.
- **Index**: kept at `<docDir>/.search-index/` (a Lucene index plus a small file recording each indexed file's last-modified time, used to skip unchanged files on the next update). It refreshes automatically every few minutes while a map is open, and whenever the Search dialog itself opens; run Tools → Scripts → Freeplane PKM → Update Next Steps and Search Index to refresh it on demand instead of waiting.
- This replaces the previous reliance on Windows Search / Inazuma Search - the index and search UI are entirely built into the add-on, so no external search tool needs to be installed.

## Development

### Requirements

- **Java**: JDK 17
- **Gradle**: 7.0 or later (can be installed automatically with the Gradle wrapper)
- **Freeplane**: Latest version (used as the script runtime environment)

### Dev Container

`.devcontainer/` is the network-isolated Claude Code dev container from [devcontainer-claude-code](https://github.com/unhurried/devcontainer-claude-code), plus JDK 17 and Gradle. Open the repository with the VS Code Dev Containers extension (`Dev Containers: Reopen in Container`).

- The container has no route to the internet; everything goes through a squid proxy that allows only the hosts in `.devcontainer/proxy/allowed-domains.txt` (GitHub, Maven Central and the Gradle plugin portal for this build, Anthropic, VS Code, ...). Add a host there and rebuild; to switch the allowlist off, copy `.devcontainer/.env.example` to `.devcontainer/.env`, set `PROXY_MODE=open` and rebuild.
- Gradle does not read `HTTPS_PROXY`, so `post-create.sh` writes the proxy into `~/.gradle/gradle.properties` (a persisted volume, like `~/.claude` and the other download caches).
- Claude Code's user-scope settings and skills come from `.devcontainer/claude/` and are synced on every container start (`sync-claude-config.sh`); the project-scope ones live in `.claude/`.

See the source repository's README for the design notes, the isolation tests and troubleshooting.

### Build and Test

```bash
gradle build   # compile + check
gradle test    # tests only
gradle check   # tests + script syntax check + codenarc + tools/check-conventions.sh
```

### Packaging as a Freeplane Add-on

```bash
gradle packageAddon                        # uses the version from gradle.properties
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
