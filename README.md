# Freeplane PKM

Freeplane PKM is a project for building a personal knowledge management (PKM) system on Freeplane, a mind-mapping tool.

### Key Features

- **Document Management**: Manage links between mind map nodes and Markdown files/directories
- **Mind Map Search and Filtering**: Search and filter mind map nodes by conditions
- **Task Management**: Reflect tasks written in Markdown files into the mind map

## Installation

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

## Related Links

- [Freeplane Official Site](https://freeplane.org/)
- [Groovy Official Site](https://groovy-lang.org/)
