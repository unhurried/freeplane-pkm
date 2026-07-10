# Freeplane PKM

Freeplane PKM is a project for building a personal knowledge management (PKM) system on Freeplane, a mind-mapping tool.

### Key Features

- **Document Management**: Manage links between mind map nodes and Markdown files/directories
- **Mind Map Search and Filtering**: Search and filter mind map nodes by conditions
- **Task Management**: Reflect tasks written in Markdown files into the mind map

## Installation

### As a Freeplane Add-on (recommended)

1. Download `freeplane-pkm-<version>.addon.mm` from the [releases page](https://github.com/unhurried/freeplane-pkm/releases).
2. In Freeplane, select Tools → Add-ons → Search and Install, choose the downloaded file, and install it.
3. Restart Freeplane.

The scripts are available under Tools → Scripts → Freeplane PKM, with keyboard shortcuts (F5–F12) assigned automatically. Note that the built-in action shortcuts in `accelerator.properties` (F1–F4) are not set by the add-on; assign them manually via Tools → Assign hot key if needed.

### Manual Installation

Register this project's scripts as Freeplane scripts.

1. Start Freeplane.
2. Select Tools → Open user directory.
3. Copy the following directories and files from this project:
  - lib/
  - scripts/
  - accelerator.properties

## Development Setup

### Requirements

- **Java**: JDK 11 or later
- **Gradle**: 7.0 or later (can be installed automatically with the Gradle wrapper)
- **Freeplane**: Latest version (used as the script runtime environment)

### Setup Steps

```bash
# Clone the project
git clone <repository-url>
cd freeplane-pkm

# (Optional) Download dependencies
gradle dependencies
```

## Build and Test

### Build the Project

```bash
gradle build
```

This command does the following:
- Compiles the source code (Groovy)
- Runs the tests
- Generates build artifacts (output to the `build/` directory)

### Run Tests

```bash
# Run all tests
gradle test

# Run only a specific test class
gradle test --tests UtilsSpec
```

## Troubleshooting

### Build Errors

```bash
# Clear the cache and rebuild
gradle clean build
```

### Test Failures

```bash
# Run tests with detailed logging
gradle test --info
```

## Packaging as a Freeplane Add-on

```bash
gradle packageAddon                        # uses addonDefaultVersion from gradle.properties
gradle packageAddon -PaddonVersion=v1.0.0  # explicit version
```

This generates `build/addon/freeplane-pkm-<version>.addon.mm` (and `version.properties`) without requiring a running Freeplane instance. The task replicates what the Freeplane devtools add-on does; see `gradle/packageAddon.gradle` for how scripts, `lib/Utils.groovy`, and the template files are embedded.

### Release via GitHub Actions

The `Package Add-on` workflow (`.github/workflows/package-addon.yml`) runs tests and packaging on every push/PR and uploads the add-on as a build artifact. Pushing a tag matching `v*` (e.g. `v1.0.0`) additionally creates a GitHub release with the `.addon.mm` file attached:

```bash
git tag v1.0.0
git push origin v1.0.0
```

## Related Links

- [Freeplane Official Site](https://freeplane.org/)
- [Groovy Official Site](https://groovy-lang.org/)
