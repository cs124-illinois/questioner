# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Questioner is an educational testing/validation framework for programming questions, primarily written in Kotlin with TypeScript components. It uses date-based versioning (YYYY.M.minor).

## Key Commands

### Building and Testing

**Kotlin/Java (root directory):**
```bash
./gradlew build        # Build entire project
./gradlew test         # Run all tests
./gradlew clean        # Clean build artifacts
./gradlew dependencies # Show dependency tree
```

**TypeScript/JavaScript (js/ directory):**
```bash
cd js
npm install           # Install all workspace dependencies
npm run build         # Build TypeScript packages
npm run check         # Run linting, type checking, and build
npm run prettier      # Format code
npm run eslint        # Lint code
npm run tsc           # Type check without emitting
```

### Publishing

```bash
./gradlew publish             # Full Maven Central release: publish, close, and release the staging repository
./gradlew publishToMavenLocal # Publish to the local Maven repository for testing
```

Before releasing, bump `version` in the root `build.gradle.kts` and the `org.cs124.questioner.settings`
version in `plugin-fixtures/settings.gradle.kts` to match.

### Running a Single Test

For Kotlin tests:
```bash
./gradlew test --tests "TestClassName.testMethodName"
```

## Architecture

The project follows a multi-module structure:

- **lib/**: Core library with question validation logic
  - Main classes: `Question`, `TestQuestion`, `Validator`, `TestResults`
  - Uses Jeed for Java/Kotlin code execution

- **plugin/**: Gradle plugin for question management
  - Contains ANTLR grammars for Java/Kotlin parsing
  - Tasks: `CollectQuestions`, `GenerateQuestionTests`, `PublishQuestions`

- **server/**: REST API server
  - MongoDB integration for data storage
  - Dockerized deployment

- **js/**: TypeScript packages using Yarn workspaces
  - types: Shared TypeScript definitions
  - output: Output formatting utilities
  - mongodb: Database integration

## Key Patterns

- **Serialization**: Moshi for JSON serialization with Kotlin data classes
- **Testing**: Kotest framework with JUnit Platform
- **Monorepo**: NPM workspaces for JavaScript packages
- **Containerization**: Docker for server components
- **JVM Settings**: Uses Java 21 with preview features enabled for testing

## Important Notes

- The project extensively uses JVM flags for testing including security manager and preview features
- Version updates should follow YYYY.M.minor format in root build.gradle.kts
- TypeScript code uses strict type checking

### The kotlin-compiler-embeddable warning is expected

Builds that apply the questioner Gradle plugin get this from the Kotlin Gradle plugin:

> `org.jetbrains.kotlin:kotlin-compiler-embeddable` Artifact Present in Build Classpath

It is structural, not a mistake, and there is nothing to fix. `lib`'s `Question` model is
jeed-typed by design (`Features`, `LineCounts`, `MutatedSource`), the plugin reads that model, and
Gradle plugins run in the build JVM, so jeed and its compiler land on the build classpath.

Do not spend time trying to remove it:

- Kotlin documents no property to suppress the warning. The only remedy it offers plugin authors is
  running compiler classes in an isolated classloader, which does not help here: isolating the two
  files that use jeed directly (`parse/ParseJava.kt`, `parse/ParseKotlin.kt`) would not remove the
  artifact, because eleven other files use `Question`.
- Making the model jeed-free is not viable. `MutatedSource` extends jeed's `Source` and its
  `formatted()` calls `googleFormat()`/`ktFormat()`, so a jeed-free model means reimplementing part
  of jeed inside questioner and keeping it in sync.

The hazard it warns about—two `kotlin-compiler-embeddable` versions sharing a classloader, jeed's
against whatever the consumer's Kotlin Gradle plugin bundles—has not caused trouble, and validation
already runs in forked JVMs where the heavy compilation happens.

## Dependency Management

- Always specify exact dependencies in package.json files, avoiding ~ or ^ version prefixes
- To check for available dependency updates, run: `./gradlew dependencyUpdates`
  - This uses the [ben-manes versions plugin](https://github.com/ben-manes/gradle-versions-plugin) and only reports stable releases
- Other dependency updates (Kotlin, Ktor, KSP, etc.) should be applied when available
