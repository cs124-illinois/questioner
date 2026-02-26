# Questioner

An educational testing and validation framework for programming questions, primarily written in Kotlin with TypeScript components.

## Overview

Questioner provides a comprehensive framework for creating, validating, and testing programming questions used in educational contexts. It supports both Java and Kotlin programming languages and includes tools for automated testing, mutation testing, and question validation.

## Project Structure

This is a multi-module project with the following components:

### Core Components

- **lib/**: Core library containing question validation logic
  - Main classes: `Question`, `TestQuestion`, `Validator`, `TestResults`
  - Uses [Jeed](https://github.com/cs125-illinois/jeed) for Java/Kotlin code execution

- **plugin/**: Gradle plugin for question management
  - Contains ANTLR grammars for Java/Kotlin parsing
  - Tasks: `CollectQuestions`, `GenerateQuestionTests`, `PublishQuestions`

- **server/**: REST API server with MongoDB integration
  - Dockerized deployment ready
  - Handles question submission and validation

- **stumperd/**: Mutation testing and deduplication system
  - Separate Docker setup for advanced testing scenarios

### TypeScript Components

- **js/types**: Shared TypeScript type definitions
- **js/output**: Output formatting utilities  
- **js/mongodb**: Database integration utilities

The JavaScript/TypeScript components use NPM workspaces for dependency management.

## Getting Started

### Prerequisites

- Java 21 or later
- Node.js 18 or later
- NPM (for TypeScript components)
- Docker (optional, for server components)

### Building the Project

**Kotlin/Java components:**
```bash
./gradlew build        # Build entire project
./gradlew test         # Run all tests
./gradlew clean        # Clean build artifacts
```

**TypeScript/JavaScript components:**
```bash
cd js
npm install           # Install all workspace dependencies
npm run build         # Build TypeScript packages
npm run check         # Run linting, type checking, and build
```

### Development Commands

**Kotlin/Java:**
```bash
./gradlew dependencies # Show dependency tree
./gradlew test --tests "TestClassName.testMethodName"  # Run specific test
```

**TypeScript/JavaScript:**
```bash
cd js
npm run prettier      # Format code
npm run eslint        # Lint code
npm run tsc           # Type check without emitting
```

## External Question Sources

The questioner plugin supports discovering questions from external directories, which is useful for incorporating questions from collaborators via Git submodules or separate repositories.

### Setup

In your `settings.gradle.kts`, use the `questioner {}` block to declare external source directories:

```kotlin
plugins {
    id("org.cs124.questioner.settings") version "2026.2.2"
}

questioner {
    external("external/alice")
    external("external/bob")
}
```

Each path is resolved relative to the project root, and the plugin automatically looks for sources under `<path>/src/main/java`. For example, `external("external/alice")` discovers questions in `external/alice/src/main/java/`.

### Excluding packages

You can exclude specific packages from an external source using the block form of `external()`. Paths are relative to the source root (`src/main/java`):

```kotlin
questioner {
    external("external/alice") {
        exclude("com/github/alice/drafts")
        exclude("com/github/alice/archived")
    }
}
```

This skips any questions under those package paths during discovery.

### Example Layout

```
my-questions/
  settings.gradle.kts          ← declares external("external/alice"), external("external/bob")
  build.gradle.kts
  src/main/java/                ← your own questions
  external/
    alice/src/main/java/        ← git submodule with Alice's questions
    bob/src/main/java/          ← git submodule with Bob's questions
```

All questions from all source directories are discovered, validated, and published together. Each question's `baseDirectory` is correctly resolved to its own source root, so imports and package resolution work as expected.

If a configured external directory doesn't exist, the plugin logs a warning and skips it rather than failing.

Each question from an external source has its `external` field set to the slug passed to `external()` (e.g., `"external/alice"`). This can be used for [filtering](#question-filtering) and is available as a transient field on the `Question` object for use in `configPublishIncludes`.

## Question Filtering

The `validate`, `validateAll`, and `validateFocused` tasks support filtering questions via Gradle project properties. Filters can be combined — only questions matching all specified filters are included.

### By name or path (`-Pfilter`)

Uses glob matching against the question's slug, full slug (`author/slug`), or file path:

```bash
./gradlew validate -Pfilter='*add-one*'
./gradlew validate -Pfilter='challen@illinois.edu/*'
```

### By author (`-Pauthor`)

Exact match on the question's `@Correct` author:

```bash
./gradlew validate -Pauthor=challen@illinois.edu
```

### By external source (`-Pexternal`)

Exact match on the slug passed to `external()` in `settings.gradle.kts`. Only validates questions from that external source:

```bash
./gradlew validate -Pexternal=external/alice
```

### Publish filtering with `configPublishIncludes`

For controlling which questions are published to which endpoints, use `configPublishIncludes` in `build.gradle.kts`. This receives the endpoint and each `Question` object, and returns whether to include it:

```kotlin
plugins {
    id("org.cs124.questioner")
}
questioner {
    configPublishIncludes { endpoint, question ->
        when (endpoint.label) {
            "production" -> question.published.author == "challen@illinois.edu"
            "staging" -> true
            else -> false
        }
    }
}
```

The `Question` object provides fields like `published.author`, `published.name`, `published.tags`, `published.packageName`, and `external` (set for questions from external sources) for filtering decisions.

## Architecture

### Question Validation Pipeline

1. **Parsing**: Questions are parsed using ANTLR grammars for Java/Kotlin
2. **Validation**: Core validation logic ensures questions meet educational standards
3. **Testing**: Automated testing verifies question correctness
4. **Mutation Testing**: Advanced testing through code mutation (stumperd)

### Technology Stack

- **Languages**: Kotlin, Java, TypeScript
- **Build System**: Gradle (Kotlin/Java), NPM (TypeScript)
- **Testing**: Kotest (Kotlin), Jest-compatible testing (TypeScript)
- **Databases**: MongoDB (server component)
- **Deployment**: Docker containers
- **Code Quality**: ESLint, Prettier, Kotlinter

## Versioning

This project uses date-based versioning following the pattern `YYYY.M.minor` (e.g., `2025.7.1`).

## Development

### Code Style

- Kotlin code follows Kotlinter standards
- TypeScript code uses ESLint 9 with flat config format
- All code is formatted using respective formatters (Kotlinter, Prettier)

### Testing

- Kotlin tests use Kotest framework
- TypeScript components include comprehensive linting and type checking
- Integration tests validate the complete question pipeline

## Docker Support

Server and stumperd components include Docker configurations for containerized deployment:

```bash
# Server component
cd server
docker build -t questioner-server .

# Stumperd component  
cd stumperd
docker-compose up
```

## Contributing

1. Follow existing code style and patterns
2. Ensure all tests pass before submitting changes
3. Update documentation as needed
4. Use the provided linting and formatting tools

## License

See the [LICENSE](LICENSE) file for license information.