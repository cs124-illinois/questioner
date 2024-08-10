import {
  CheckstyleFailed,
  CheckstyleResults,
  CompilationFailed,
  CompiledSourceResult,
  KtlintFailed,
  KtlintResults,
  TemplatingFailed,
} from "@cs124/jeed-types"
import {
  Array,
  Boolean,
  Literal,
  Number,
  Partial,
  Record,
  Array as RuntypeArray,
  Static,
  String,
  Union,
} from "runtypes"
import { Languages } from "./languages"
import { Step, TestTestingStep } from "./steps"
import { SubmissionType } from "./submission"
import { TestResults } from "./testresults"

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

export const TestTestingCompletedTasks = Partial({
  // templateSubmission doesn't complete
  compileSubmission: CompiledSourceResult,
  checkstyle: CheckstyleResults,
  ktlint: KtlintResults,
  // checkCompiledSubmission doesn't complete
  testTesting: TestTestingResult,
})
export type TestTestingCompletedTasks = Static<typeof TestTestingCompletedTasks>

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
  "extraOutput",
]

export const stepToKey = (step: Step): string => {
  if (step === "executioncount") {
    return "executionCount"
  } else {
    return step
  }
}

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

export * from "./coordinates"
export * from "./languages"
export * from "./question"
export * from "./steps"
export * from "./submission"
export * from "./testresults"
