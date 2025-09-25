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

- **stumperd/**: Mutation testing and deduplication
  - Separate Docker setup

- **js/**: TypeScript packages using Yarn workspaces
  - types: Shared TypeScript definitions
  - output: Output formatting utilities
  - mongodb: Database integration
  - stumperd-import: Import utilities

## Key Patterns

- **Serialization**: Moshi for JSON serialization with Kotlin data classes
- **Testing**: Kotest framework with JUnit Platform
- **Monorepo**: NPM workspaces for JavaScript packages
- **Containerization**: Docker for server components
- **JVM Settings**: Uses Java 21 with preview features enabled for testing

## Important Notes

- The project extensively uses JVM flags for testing including security manager and preview features
- Version updates should follow YYYY.M.minor format in root build.gradle.kts
- Server and stumperd modules have their own Docker configurations
- TypeScript code uses strict type checking

## Dependency Management

- Always specify exact dependencies in package.json files, avoiding ~ or ^ version prefixes

- We're currently ignoring dependency updates from Kotlin, ktor, KSP, and kotest.
