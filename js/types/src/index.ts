import {
  CheckstyleFailed,
  CheckstyleResults,
  CompilationFailed,
  CompiledSourceResult,
  FeatureValue,
  KtlintFailed,
  KtlintResults,
  TemplatingFailed,
} from "@cs124/jeed-types"
import {
  Array,
  Boolean,
  Dictionary,
  Literal,
  Number,
  Partial,
  Record,
  Array as RuntypeArray,
  Static,
  String,
  Union,
} from "runtypes"
import { SubmissionType } from "./submission"
import { Languages } from "./languages"

export const LanguagesResourceUsage = Record({
  java: Number,
}).And(
  Partial({
    kotlin: Number,
  }),
)
export type LanguagesResourceUsage = Static<typeof LanguagesResourceUsage>

export const MethodInfo = Record({
  className: String,
  methodName: String,
  descriptor: String,
})
export type MethodInfo = Static<typeof MethodInfo>

export const LineCoverage = Record({
  covered: Number,
  total: Number,
  missed: Number,
})
export type LineCoverage = Static<typeof LineCoverage>

export const ValidationResults = Record({
  seed: Number,
  requiredTestCount: Number,
  mutationCount: Number,
  solutionMaxRuntime: Number,
  bootstrapLength: Number,
  mutationLength: Number,
  incorrectLength: Number,
  calibrationLength: Number,
  solutionCoverage: LineCoverage,
  executionCounts: LanguagesResourceUsage,
  memoryAllocation: LanguagesResourceUsage,
}).And(
  Partial({
    solutionMaxClassSize: LanguagesResourceUsage,
    canTestTest: Boolean,
  }),
)
export type ValidationResults = Static<typeof ValidationResults>

export const QuestionPath = Record({
  path: String,
  author: String,
  version: String,
})
export type QuestionPath = Static<typeof QuestionPath>

export const QuestionDescription = QuestionPath.And(
  Record({
    name: String,
    description: String,
    packageName: String,
    starter: String,
  }),
)
export type QuestionDescription = Static<typeof QuestionDescription>

export const LineCounts = Record({
  source: Number,
  comment: Number,
  blank: Number,
})
export type LineCounts = Static<typeof LineCounts>

export const Citation = Record({ source: String }).And(Partial({ link: String }))
export type Citation = Static<typeof Citation>

export const QuestionType = Union(Literal("SNIPPET"), Literal("METHOD"), Literal("KLASS"))
export type QuestionType = Static<typeof QuestionType>

export const QuestionPublished = QuestionPath.And(
  Record({
    contentHash: String,
    name: String,
    type: QuestionType,
    packageName: String,
    languages: RuntypeArray(Languages),
    descriptions: Dictionary(String, Languages),
    templateImports: RuntypeArray(String),
    questionerVersion: String,
    authorName: String,
    klass: String,
  }),
).And(
  Partial({
    citation: Citation,
    starters: Dictionary(String, Languages),
    tags: Array(String),
  }),
)
export type QuestionPublished = Static<typeof QuestionPublished>

export const QuestionClassification = Record({
  featuresByLanguage: Dictionary(FeatureValue, Languages),
  complexity: Dictionary(Number, Languages),
  lineCounts: Dictionary(LineCounts, Languages),
}).And(
  Partial({
    recursiveMethodsByLanguage: Dictionary(RuntypeArray(MethodInfo), Languages),
    loadedClassesByLanguage: Dictionary(RuntypeArray(String), Languages),
  }),
)
export type QuestionClassification = Static<typeof QuestionClassification>

export const QuestionMetadata = Record({
  unusedFiles: RuntypeArray(String),
}).And(
  Partial({
    focused: Boolean,
    publish: Boolean,
  }),
)
export type QuestionMetadata = Static<typeof QuestionMetadata>

export const Question = Record({
  published: QuestionPublished,
  classification: QuestionClassification,
}).And(
  Partial({
    validationResults: ValidationResults,
  }),
)
export type Question = Static<typeof Question>

export const SelectionStrategy = Union(Literal("HARDEST"), Literal("EASIEST"), Literal("EVENLY_SPACED"))
export type SelectionStrategy = Static<typeof SelectionStrategy>

export const TestTestingSettings = Partial({
  shortCircuit: Boolean,
  limit: Number,
  SelectionStrategy: SelectionStrategy,
  seed: Number,
})
export type TestTestingSettings = Static<typeof TestTestingSettings>

export const Submission = Record({
  type: SubmissionType,
  contentHash: String,
  language: Languages,
  contents: String,
}).And(
  Partial({
    testTestingSettings: TestTestingSettings,
  }),
)
export type Submission = Static<typeof Submission>

export const TestResult = Record({
  name: String,
  passed: Boolean,
  type: Union(
    Literal("CONSTRUCTOR"),
    Literal("INITIALIZER"),
    Literal("METHOD"),
    Literal("STATIC_METHOD"),
    Literal("FACTORY_METHOD"),
    Literal("COPY_CONSTRUCTOR"),
  ),
  runnerID: Number,
  stepCount: Number,
  methodCall: String,
}).And(
  Partial({
    message: String,
    arguments: String,
    expected: String,
    found: String,
    explanation: String,
    output: String,
    stdin: String,
    complexity: Number,
    submissionStackTrace: String,
    differs: RuntypeArray(
      Union(
        Literal("STDOUT"),
        Literal("STDERR"),
        Literal("INTERLEAVED_OUTPUT"),
        Literal("RETURN"),
        Literal("THREW"),
        Literal("PARAMETERS"),
        Literal("VERIFIER_THREW"),
        Literal("INSTANCE_VALIDATION_THREW"),
      ),
    ),
  }),
)
export type TestResult = Static<typeof TestResult>

export const TestingResult = Record({
  tests: RuntypeArray(TestResult),
  testCount: Number,
  completed: Boolean,
  passed: Boolean,
})
export type TestingResult = Static<typeof TestingResult>

export const TestTestingResult = Record({
  correct: Number,
  incorrect: Number,
  total: Number,
  duration: Number,
  succeeded: Boolean,
  shortCircuited: Boolean,
  output: Array(String),
}).And(
  Partial({
    identifiedSolution: Boolean,
  }),
)
export type TestTestingResult = Static<typeof TestTestingResult>

export const ClassSizeComparison = Record({
  solution: Number,
  submission: Number,
  limit: Number,
  increase: Number,
  failed: Boolean,
})
export type ClassSizeComparison = Static<typeof ClassSizeComparison>

export const MemoryAllocationComparison = Record({
  solution: Number,
  submission: Number,
  limit: Number,
  increase: Number,
  failed: Boolean,
})
export type MemoryAllocationComparison = Static<typeof MemoryAllocationComparison>

export const ExecutionCountComparison = Record({
  solution: Number,
  submission: Number,
  limit: Number,
  increase: Number,
  failed: Boolean,
})
export type ExecutionCountComparison = Static<typeof ExecutionCountComparison>

export const CoverageComparison = Record({
  solution: LineCoverage,
  submission: LineCoverage,
  missed: RuntypeArray(Number),
  limit: Number,
  increase: Number,
  failed: Boolean,
})
export type CoverageComparison = Static<typeof CoverageComparison>

export const LineCountComparison = Record({
  solution: LineCounts,
  submission: LineCounts,
  limit: Number,
  allowance: Number,
  increase: Number,
  failed: Boolean,
})
export type LineCountComparison = Static<typeof LineCountComparison>

export const ComplexityComparison = Record({
  solution: Number,
  submission: Number,
  limit: Number,
  increase: Number,
  failed: Boolean,
})
export type ComplexityComparison = Static<typeof ComplexityComparison>

export const FeaturesComparison = Record({
  errors: RuntypeArray(String),
  failed: Boolean,
})
export type FeaturesComparison = Static<typeof FeaturesComparison>

export const RecursionComparison = Record({
  missingMethods: RuntypeArray(String),
  failed: Boolean,
})
export type RecursionComparison = Static<typeof RecursionComparison>

export const PassedTestCount = Record({
  passed: Number,
  total: Number,
  completed: Boolean,
})
export type PassedTestCount = Static<typeof PassedTestCount>

export const PassedMutantCount = Record({
  passed: Number,
  total: Number,
  completed: Boolean,
})
export type PassedMutantCount = Static<typeof PassedMutantCount>

export const PassedSteps = Record({
  compiled: Boolean,
  design: Boolean,
  partiallyCorrect: Boolean,
  fullyCorrect: Boolean,
  quality: Boolean,
})
export type PassedSteps = Static<typeof PassedSteps>

export const PartialCredit = Record({
  passedSteps: PassedSteps,
}).And(
  Partial({
    passedTestCount: PassedTestCount,
    passedMutantCount: PassedMutantCount,
  }),
)
export type PartialCredit = Static<typeof PartialCredit>

export const CompletedTasks = Partial({
  // templateSubmission doesn't complete
  compileSubmission: CompiledSourceResult,
  checkstyle: CheckstyleResults,
  ktlint: KtlintResults,
  // checkCompiledSubmission doesn't complete
  classSize: ClassSizeComparison,
  complexity: ComplexityComparison,
  features: FeaturesComparison,
  lineCount: LineCountComparison,
  partial: PartialCredit,
  // execution
  // checkExecutedSubmission doesn't complete
  recursion: RecursionComparison,
  executionCount: ExecutionCountComparison,
  memoryAllocation: MemoryAllocationComparison,
  testing: TestingResult,
  coverage: CoverageComparison,
})
export type CompletedTasks = Static<typeof CompletedTasks>

export const TestTestingCompletedTasks = Partial({
  // templateSubmission doesn't complete
  compileSubmission: CompiledSourceResult,
  checkstyle: CheckstyleResults,
  ktlint: KtlintResults,
  // checkCompiledSubmission doesn't complete
  testTesting: TestTestingResult,
})
export type TestTestingCompletedTasks = Static<typeof TestTestingCompletedTasks>

export const FailedTasks = Partial({
  checkInitialSubmission: String,
  templateSubmission: TemplatingFailed,
  compileSubmission: CompilationFailed,
  checkstyle: CheckstyleFailed,
  ktlint: KtlintFailed,
  checkCompiledSubmission: String,
  classSize: String,
  complexity: String,
  features: String,
  lineCount: String,
  // execution
  checkExecutedSubmission: String,
  // executionCount doesn't fail
  // memoryAllocation doesn't fail
  // testing doesn't fail
  // coverage doesn't fail
})
export type FailedTasks = Static<typeof FailedTasks>

export const TestTestingFailedTasks = Partial({
  checkInitialSubmission: String,
  templateSubmission: TemplatingFailed,
  compileSubmission: CompilationFailed,
  checkstyle: CheckstyleFailed,
  ktlint: KtlintFailed,
  checkCompiledSubmission: String,
  checkExecutedSubmission: String,
})
export type TestTestingFailedTasks = Static<typeof TestTestingFailedTasks>

export const Step = Union(
  Literal("checkInitialSubmission"),
  Literal("templateSubmission"),
  Literal("compileSubmission"),
  Literal("checkstyle"),
  Literal("ktlint"),
  Literal("checkCompiledSubmission"),
  Literal("classSize"),
  Literal("complexity"),
  Literal("features"),
  Literal("lineCount"),
  Literal("partial"),
  // execution
  Literal("checkExecutedSubmission"),
  Literal("recursion"),
  Literal("executioncount"),
  Literal("memoryAllocation"),
  Literal("testing"),
  Literal("coverage"),
)
export type Step = Static<typeof Step>

export const TestTestingStep = Union(
  Literal("checkInitialSubmission"),
  Literal("templateSubmission"),
  Literal("compileSubmission"),
  Literal("checkstyle"),
  Literal("ktlint"),
  Literal("checkCompiledSubmission"),
  Literal("checkExecutedSubmission"),
  Literal("testTesting"),
)
export type TestTestingStep = Static<typeof TestTestingStep>

export const TestingOrder: Step[] = [
  "checkInitialSubmission",
  "templateSubmission",
  "compileSubmission",
  "checkstyle",
  "ktlint",
  "checkCompiledSubmission",
  "classSize",
  "complexity",
  "features",
  "lineCount",
  "partial",
  // execution
  "checkExecutedSubmission",
  "recursion",
  "executioncount",
  "memoryAllocation",
  "testing",
  "coverage",
]

export const stepToKey = (step: Step): string => {
  if (step === "executioncount") {
    return "executionCount"
  } else {
    return step
  }
}

export const TestResults = Record({
  language: Languages,
  completedSteps: RuntypeArray(Step),
  complete: CompletedTasks,
  failedSteps: RuntypeArray(Step),
  failed: FailedTasks,
  skippedSteps: RuntypeArray(Step),
  timeout: Boolean,
  completed: Boolean,
  succeeded: Boolean,
}).And(
  Partial({
    failedLinting: Boolean,
    failureCount: Number,
    lineCountTimeout: Boolean,
  }),
)
export type TestResults = Static<typeof TestResults>

export const TestTestResults = Record({
  language: Languages,
  completedSteps: RuntypeArray(TestTestingStep),
  complete: TestTestingCompletedTasks,
  failedSteps: RuntypeArray(TestTestingStep),
  failed: TestTestingFailedTasks,
  skippedSteps: RuntypeArray(TestTestingStep),
  timeout: Boolean,
  lineCountTimeout: Boolean,
  completed: Boolean,
  succeeded: Boolean,
}).And(
  Partial({
    failedLinting: Boolean,
  }),
)
export type TestTestResults = Static<typeof TestTestResults>

export const TerminalOutput = Record({
  error: Boolean,
  retry: Boolean,
  output: String,
})
export type TerminalOutput = Static<typeof TerminalOutput>

export const CacheStats = Record({
  hits: Number,
  misses: Number,
})
export type CacheStats = Static<typeof CacheStats>

export const ServerResponse = Record({
  type: SubmissionType,
  canCache: Boolean,
  cacheStats: CacheStats,
  duration: Number,
  version: String,
}).And(
  Partial({
    solveResults: TestResults,
    testTestingResults: TestTestResults,
  }),
)
export type ServerResponse = Static<typeof ServerResponse>

export const QuestionTagged = QuestionPublished.And(
  Record({
    tags: Array(String),
  }),
)
export type QuestionTagged = Static<typeof QuestionTagged>

export * from "./submission"
export * from "./languages"
export * from "./coordinates"
