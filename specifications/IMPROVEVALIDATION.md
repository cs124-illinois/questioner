# Improve Validation Architecture

## Problem Statement

Currently, question validation uses a template-based test generation approach:
1. `GenerateQuestionTests` task generates three Kotest test files (`TestAllQuestions.kt`, `TestUnvalidatedQuestions.kt`, `TestFocusedQuestions.kt`)
2. Each file contains generated StringSpec tests with one test case per question
3. Each test case calls `validate()` on a question's JSON file path
4. This approach is "gross" and requires code generation

**Goal:** Replace the template generation with a proper Gradle-based validation approach that still appears in IntelliJ's test UI pane.

## Research Findings

### How IntelliJ Integrates with Gradle Tests

1. **Tooling API**: IntelliJ uses Gradle's Tooling API to receive test events
2. **JUnit Platform Integration**: When a Gradle Test task uses `useJUnitPlatform()`, test events are automatically forwarded
3. **TestEngine Discovery**: Any `TestEngine` registered via Java ServiceLoader is automatically discovered
4. **No IDE-Specific Code**: The integration "just works" through standard JUnit Platform mechanisms

### Gradle Test Event Reporting Options

Two main approaches for custom test-like tasks:

#### Option 1: Custom JUnit Platform TestEngine (Recommended)
- Implement `org.junit.platform.engine.TestEngine` interface
- Register via ServiceLoader in `META-INF/services/org.junit.platform.engine.TestEngine`
- Automatically works with Gradle's Test task
- Appears seamlessly in IntelliJ's test runner
- Runs in separate JVM with all Test task configuration

#### Option 2: Gradle TestEventReporter API
- Use `TestEventReporterFactory` in a custom Gradle task
- More recent API (Gradle 6.1+)
- More complex to set up
- Requires manual event emission
- May have IDE integration quirks

### Current Validation Process

The validation is comprehensive but NOT actually "testing" - it's **quality assurance validation**:

**Phase 1: Bootstrap**
- Load question from JSON
- Test all solutions (primary + alternates)
- Verify compilation, linting, entropy
- Measure resource usage, coverage

**Phase 2: Mutation Testing**
- Generate mutations using Jeed
- Validate minimum mutation count achieved

**Phase 3: Incorrect Examples Testing**
- Test all incorrect examples (mutations + manual `@Incorrect`)
- Verify each fails for expected reason (COMPILE, TEST, TIMEOUT, etc.)
- Determine required test count

**Phase 4: Calibration**
- Re-run solutions with determined test count
- Set final limits for student submissions

**Phase 5: Results Storage**
- Save validation results to question JSON
- Generate HTML report
- Mark as `validated = true`

## Proposed Solution: Custom JUnit Platform TestEngine

### Architecture Overview

```
QuestionerTestEngine (implements TestEngine)
    ↓
Discovers questions from questions.json
    ↓
Creates TestDescriptor hierarchy
    ↓
Executes validation via existing validate() function
    ↓
Reports results through JUnit Platform
    ↓
IntelliJ/IDE displays in test UI
```

### Implementation Components

#### 1. QuestionerTestEngine
**Location:** `lib/src/main/kotlin/edu/illinois/cs/cs125/questioner/lib/engine/QuestionerTestEngine.kt`

```kotlin
class QuestionerTestEngine : TestEngine {
    override fun getId(): String = "questioner-validation"

    override fun discover(
        discoveryRequest: EngineDiscoveryRequest,
        uniqueId: UniqueId
    ): TestDescriptor {
        // Load questions.json
        // Create root descriptor
        // Create child descriptors for each question
        // Support filtering via selectors/tags
    }

    override fun execute(executionRequest: ExecutionRequest) {
        // For each question descriptor
        //   Start test execution
        //   Call existing validate() function
        //   Report success/failure
        //   Catch exceptions and report
    }
}
```

**Key Features:**
- Read `questions.json` location from system property
- Read validation options (maxMutationCount, retries, verbose) from system properties
- Create hierarchical test structure (engine → all questions → individual question)
- Tag questions as "unvalidated" or "focused" for filtering
- Reuse existing `validate()` function completely unchanged

#### 2. ServiceLoader Registration
**Location:** `lib/src/main/resources/META-INF/services/org.junit.platform.engine.TestEngine`

```
edu.illinois.cs.cs125.questioner.lib.engine.QuestionerTestEngine
```

#### 3. Gradle Plugin Updates
**Modify:** `plugin/src/main/kotlin/QuestionerPlugin.kt`

Changes:
- Remove `GenerateQuestionTests` task registration
- Remove dependency on `generateQuestionTests` from Test tasks
- Pass configuration via system properties to Test tasks:
  ```kotlin
  testTask.systemProperty("questioner.questions.json", collectionsJsonPath)
  testTask.systemProperty("questioner.maxMutationCount", config.maxMutationCount)
  testTask.systemProperty("questioner.retries", config.retries)
  testTask.systemProperty("questioner.verbose", config.verbose)
  testTask.systemProperty("questioner.rootDirectory", rootDir)
  ```
- Update test task filtering:
  ```kotlin
  // For testUnvalidatedQuestions
  testTask.useJUnitPlatform {
      includeTags("unvalidated")
  }

  // For testFocusedQuestions
  testTask.useJUnitPlatform {
      includeTags("focused")
  }
  ```

#### 4. Remove GenerateQuestionTests
**Delete:** `plugin/src/main/kotlin/GenerateQuestionTests.kt`

No longer needed.

#### 5. Update Dependencies
**Modify:** `lib/build.gradle.kts`

Add JUnit Platform dependencies if not already present:
```kotlin
implementation("org.junit.platform:junit-platform-engine:1.10.x")
```

### Test Task Configuration

Three test tasks will still exist but work differently:

1. **test** (default) / **testUnvalidatedQuestions**
   - Uses JUnit Platform tag: `@Tag("unvalidated")`
   - Discovers only questions where `validated == false`

2. **testAllQuestions**
   - No tag filtering
   - Discovers all questions

3. **testFocusedQuestions**
   - Uses JUnit Platform tag: `@Tag("focused")`
   - Discovers only questions where `metadata?.focused == true`

### Benefits

1. **No Code Generation**: Questions discovered dynamically from JSON
2. **Seamless IDE Integration**: Works automatically in IntelliJ via JUnit Platform
3. **Separate JVM**: Gradle Test task already configured with all necessary JVM flags
4. **Cleaner Architecture**: Single responsibility - TestEngine discovers, validation validates
5. **Maintainable**: ~200-300 lines vs. template generation complexity
6. **Reuses Existing Code**: `validate()` function unchanged
7. **Better Developer Experience**: Real-time test discovery in IDE

### Challenges & Considerations

1. **JUnit Platform Version**: Ensure compatibility with current Kotest version (if Kotest still used elsewhere)
2. **Test Descriptors**: Need proper test IDs for stable IDE test history
3. **Concurrency**: May need to handle Kotest's concurrency settings differently
4. **Cache Statistics**: Currently tracked in beforeSpec/afterSpec - need alternative approach
5. **Error Reporting**: Ensure validation failures display nicely in test UI

### Migration Path

1. Implement `QuestionerTestEngine` in lib module
2. Add ServiceLoader registration
3. Update Gradle plugin to pass config via system properties
4. Test with existing questions
5. Once verified, remove `GenerateQuestionTests` task
6. Remove generated test file cleanup from `.gitignore`
7. Update documentation

### Alternative Considered: TestEventReporter API

Using Gradle's newer `TestEventReporter` API was considered but rejected because:
- More complex implementation
- Requires manual event construction
- TestEngine approach is more idiomatic for test frameworks
- Better IDE integration track record
- Aligns with how other testing frameworks work (JUnit, TestNG, Spock)

## Current State Analysis

### Files Involved

1. **plugin/src/main/kotlin/GenerateQuestionTests.kt** (122 lines)
   - Generates three test files
   - Creates Kotest StringSpec classes
   - Adds cache statistics tracking
   - Configures concurrency

2. **plugin/src/main/kotlin/QuestionerPlugin.kt** (line 75-76, 181-199)
   - Registers test tasks
   - Configures dependencies on generateQuestionTests
   - Sets up test filtering

3. **lib/src/main/kotlin/Validator.kt**
   - Core validation logic
   - Entry point: `suspend fun String.validate(options: ValidatorOptions)`
   - Completely reusable

### Test Execution Flow (Current)

```
collectQuestions
    ↓
generateQuestionTests (creates .kt files)
    ↓
compileTestKotlin
    ↓
Gradle test task (runs Kotest)
    ↓
Kotest discovers StringSpec tests
    ↓
Each test calls validate()
    ↓
Results reported to Gradle/IDE
```

### Test Execution Flow (Proposed)

```
collectQuestions
    ↓
Gradle test task
    ↓
JUnit Platform discovers QuestionerTestEngine
    ↓
TestEngine reads questions.json
    ↓
TestEngine creates test descriptors
    ↓
TestEngine executes: calls validate() for each question
    ↓
Results reported to Gradle/IDE
```

## Implementation Estimate

- **QuestionerTestEngine**: ~250 lines
- **ServiceLoader registration**: 1 line
- **Plugin updates**: ~50 lines changed/removed
- **Dependency updates**: ~5 lines
- **Delete GenerateQuestionTests**: -122 lines

**Net change**: ~180 lines added, ~120 lines removed

**Complexity**: Medium (requires understanding JUnit Platform API)

**Risk**: Low (can test alongside existing approach before removing)

## Alternative Architecture: Per-Question Task Pipelines

The TestEngine approach above improves validation execution, but the task pipeline itself could also be restructured for better incremental builds and developer experience.

### Current Pipeline Issues

**Current flow:**
```
saveQuestions (all questions) → collectQuestions → generateQuestionTests → validate (all questions)
```

**Problems:**
1. Can't validate a single question without processing all questions
2. Changing one question triggers regeneration of all test files
3. Gradle can't track individual question inputs/outputs properly
4. No easy way to validate questions matching a pattern (e.g., "all Fall2025 questions")

### Proposed: Per-Question Task Pipeline

**New flow:**
```
For each question:
  saveQuestion:Fall2025:Homework1:AddOne
      ↓
  validateQuestion:Fall2025:Homework1:AddOne
```

### Key Architecture Changes

#### 1. Fast Question Discovery

Replace the monolithic `saveQuestions` task with per-question registration:

```kotlin
// Fast discovery using simple text search
val questionSourceFiles = sourceDir.walk()
    .filter { it.extension in listOf("java", "kt") }
    .filter { it.readText().contains("@Correct") }  // Fast text scan
    .toList()

// Register per-question tasks
questionSourceFiles.forEach { sourceFile ->
    val relativePath = sourceFile.relativeTo(srcDir).parent
    val questionId = relativePath.toString().replace(File.separator, ":")

    // Individual save task
    tasks.register("saveQuestion:$questionId", SaveSingleQuestion::class) {
        inputSourceFile.set(sourceFile)
        outputJsonFile.set(buildDir.resolve("questioner/questions/$relativePath/.question.json"))
    }

    // Individual validate task
    tasks.register("validateQuestion:$questionId", ValidateSingleQuestion::class) {
        dependsOn("saveQuestion:$questionId")
        inputJsonFile.set(buildDir.resolve("questioner/questions/$relativePath/.question.json"))
        outputReportFile.set(buildDir.resolve("questioner/reports/$relativePath/report.html"))
    }
}
```

**Discovery performance:** The `@Correct` text search is very fast (no ANTLR parsing). Full validation happens only when tasks execute.

#### 2. Move .question.json to Build Directory

**Current:** `.question.json` files scattered in source tree next to Question.java
**Proposed:** All outputs in `build/questioner/questions/`

**Benefits:**
- Clean source tree (no generated files)
- Follows Gradle conventions (build outputs go in build/)
- Better .gitignore handling
- Easier cleanup

**Structure:**
```
build/
  questioner/
    questions/           # Parsed question JSON
      Fall2025/
        Homework1/
          AddOne/.question.json
          Fibonacci/.question.json
    reports/            # Validation HTML reports
      Fall2025/
        Homework1/
          AddOne/report.html
    hashes/             # For deduplication (see below)
      abc123def.txt
      xyz789ghi.txt
```

#### 3. Hash-Based Deduplication

**Challenge:** Need to detect duplicate questions across different paths (e.g., same question used in multiple semesters).

**Current approach (CollectQuestions):**
- Loads all .question.json files
- Checks content hashes
- Throws error if duplicates found

**Proposed: Filesystem-Based Deduplication**

Use the filesystem itself to detect duplicates during save:

```kotlin
tasks.register("saveQuestion:$questionId", SaveSingleQuestion::class) {
    inputSourceFile.set(sourceFile)
    questionJsonFile.set(buildDir.resolve("questioner/questions/$relativePath/.question.json"))

    doLast {
        // Parse and save question
        val question = parseQuestion()
        question.writeToFile(questionJsonFile.get().asFile)

        // Write to hash-indexed file for deduplication
        val hash = question.published.contentHash
        val hashFile = buildDir.resolve("questioner/hashes/$hash.txt")

        hashFile.parentFile.mkdirs()
        hashFile.appendText("${question.correctPath}\n")
    }
}

// Deduplication check task
tasks.register("checkDuplicateQuestions") {
    dependsOn(tasks.matching { it.name.startsWith("saveQuestion:") })

    doLast {
        buildDir.resolve("questioner/hashes").listFiles()?.forEach { hashFile ->
            val paths = hashFile.readLines().filter { it.isNotBlank() }
            if (paths.size > 1) {
                throw GradleException(
                    """
                    Duplicate question content (hash: ${hashFile.nameWithoutExtension}):
                    ${paths.joinToString("\n") { "  - $it" }}
                    """.trimIndent()
                )
            }
        }
    }
}
```

**How it works:**
1. Each `saveQuestion` task appends its path to `hashes/<contenthash>.txt`
2. If a hash file contains multiple lines → duplicate questions
3. Simple, fast, leverages filesystem
4. Hash files are self-documenting

**When to run:** Before publishing or as part of CI validation

#### 4. Eliminate questions.json

**Current:** `collectQuestions` creates a single `questions.json` with all questions

**Analysis of consumers:**
- `GenerateQuestionTests` - Would be replaced by TestEngine (reads .question.json files directly)
- `PublishQuestions` - Could glob for .question.json files
- `DumpQuestions` - Could glob for .question.json files
- `PrintSlowQuestions` - Could glob for .question.json files
- `ShowUpdatedSeeds` - Could glob for .question.json files

**All consumers just do:**
```kotlin
inputFile.loadQuestionList()
    .filter { /* criteria */ }
```

**Could be rewritten as:**
```kotlin
fileTree(buildDir.resolve("questioner/questions")) {
    include("**/.question.json")
}.map { it.loadQuestion()!! }
 .filter { /* same criteria */ }
```

**Conclusion:** `questions.json` is a convenience cache, not architecturally necessary.

#### 5. Glob-Based Validation

With per-question tasks, we can validate by pattern:

**Approach 1: Gradle's Built-in Wildcards**
```bash
./gradlew validateQuestion:Fall2025:*
./gradlew validateQuestion:*:Homework1:*
```

**Approach 2: Custom validateGlob Task (Recommended)**
```bash
./gradlew validateGlob -Ppattern="**/Fall2025/**"
./gradlew validateGlob -Ppattern="**/Homework1/**"
./gradlew validateGlob -Ppattern="Fall2025/Homework1/*"
```

Implementation:
```kotlin
tasks.register("validateGlob") {
    doFirst {
        val pattern = project.findProperty("pattern") as? String ?: "**"
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")

        val matchingTasks = tasks.matching {
            it.name.startsWith("validateQuestion:") &&
            matcher.matches(Paths.get(it.name.removePrefix("validateQuestion:").replace(":", "/")))
        }

        if (matchingTasks.isEmpty()) {
            throw GradleException("No questions matched pattern: $pattern")
        }

        println("Validating ${matchingTasks.size} questions matching: $pattern")
        dependsOn(matchingTasks)
    }
}
```

**Approach 3: Environment Shortcuts**
```bash
./gradlew validateSemester -Psemester=Fall2025
./gradlew validateAssignment -Passignment=Homework1
```

**Bonus: IDE Integration**

IntelliJ's Gradle panel would show:
```
📁 validateQuestion
  📁 Fall2025
    📁 Homework1
      ▶️ AddOne
      ▶️ Fibonacci
    📁 Homework2
      ▶️ Palindrome
```

Right-click any folder → "Run all tasks in group" = instant glob validation!

### Benefits of Per-Question Architecture

1. ✅ **Single-question validation**: `./gradlew validateQuestion:Fall2025:Homework1:AddOne`
2. ✅ **Glob-based validation**: `./gradlew validateGlob -Ppattern="**/Fall2025/**"`
3. ✅ **Proper incremental builds**: Gradle only reprocesses changed questions
4. ✅ **Build cache support**: Each question's outputs cached independently
5. ✅ **Parallel execution**: Gradle handles automatically (better than WorkerExecutor)
6. ✅ **Clean source tree**: All outputs in build/
7. ✅ **Fast discovery**: Simple text search for @Correct
8. ✅ **Filesystem-based deduplication**: No separate validation step needed
9. ✅ **Better IDE integration**: Task tree shows question hierarchy
10. ✅ **Eliminates questions.json**: Tasks glob for .question.json files directly

### Implementation Considerations

**Task Registration Performance:**
- Need to discover questions before registering tasks
- Discovery is fast (text search only, no ANTLR)
- For 100 questions: ~200 task objects (saveQuestion + validateQuestion)
- Gradle handles this well, minimal overhead

**Cross-Question Validation:**
- Uniqueness checking still needed but runs only on demand
- Can be separate `checkDuplicateQuestions` task
- Optional for local dev, mandatory for publishing/CI

**Backward Compatibility:**
- Can keep aggregate tasks (`validateAll`, `validateUnvalidated`)
- These become thin wrappers that depend on per-question tasks
- Smooth migration path

### Proposed Task Structure

```
Question Discovery (fast text scan)
    ↓
Register per-question tasks:
  - saveQuestion:<path>
  - validateQuestion:<path>
    ↓
Aggregate tasks (optional):
  - saveAllQuestions → depends on all saveQuestion:*
  - validateAllQuestions → depends on all validateQuestion:*
  - validateUnvalidated → depends on unvalidated validateQuestion:*
  - validateGlob -Ppattern="..." → dynamic dependency resolution
    ↓
Validation checks (pre-publish):
  - checkDuplicateQuestions → validates hash uniqueness
  - checkQuestionMetadata → validates other cross-question constraints
```

### Combining with TestEngine Approach

These two improvements are complementary:

1. **Per-Question Tasks**: Better Gradle integration, incremental builds, glob validation
2. **TestEngine**: Better IDE test UI, eliminates code generation

**Combined architecture:**
```
saveQuestion:X → validateQuestion:X
                      ↓
                 Updates .question.json
                      ↓
              TestEngine discovers all .question.json
                      ↓
              Shows in IDE test panel
```

Or, **TestEngine could replace validateQuestion tasks entirely:**
- Per-question save tasks (still useful for incremental builds)
- TestEngine handles all validation (via test UI)
- Best of both worlds: Gradle task granularity + IDE test UI

### Migration Strategy

**Phase 1: Per-Question Save Tasks**
1. Implement fast discovery
2. Register per-question `saveQuestion:*` tasks
3. Keep `collectQuestions` for now
4. Test incremental builds work correctly

**Phase 2: Move Outputs to Build Directory**
1. Update `saveQuestion` to output to `build/questioner/questions/`
2. Update consumers to glob for .question.json files
3. Remove `collectQuestions` task
4. Update .gitignore

**Phase 3: Hash-Based Deduplication**
1. Add hash file writing to `saveQuestion`
2. Implement `checkDuplicateQuestions` task
3. Add to publish/CI workflows

**Phase 4: Per-Question Validation**
1. Implement `validateQuestion:*` tasks OR
2. Implement TestEngine (which discovers questions directly)
3. Remove `generateQuestionTests`

**Phase 5: Glob Support**
1. Implement `validateGlob` task
2. Optional: Add convenience tasks for common patterns
3. Document usage

## References

- [Gradle Test Event Reporting API](https://docs.gradle.org/current/userguide/test_reporting_api.html)
- [JUnit Platform TestEngine API](https://junit.org/junit5/docs/current/api/org/junit/platform/engine/TestEngine.html)
- [Custom JUnit TestEngine Tutorial](https://www.marianlambert.com/blog/custom-junit-test-engine)
- [Gradle JUnit Platform Integration](https://docs.gradle.org/current/userguide/java_testing.html#using_junit5)
- [Gradle Task Configuration Avoidance](https://docs.gradle.org/current/userguide/task_configuration_avoidance.html)
- [Gradle Build Cache](https://docs.gradle.org/current/userguide/build_cache.html)
