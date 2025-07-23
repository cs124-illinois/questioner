import {
  CheckstyleFailed,
  CheckstyleResults,
  CompilationFailed,
  CompiledSourceResult,
  KtlintFailed,
  KtlintResults,
  TemplatingFailed,
} from "@cs124/jeed-types"
import { Array, Boolean, Literal, Number, Object, Record, Static, String, Union } from "runtypes"
import { Languages } from "./languages"
import { TestTestingStep } from "./steps"

export const SelectionStrategy = Union(
  Literal("HARDEST"),
  Literal("EASIEST"),
  Literal("EVENLY_SPACED"),
  Literal("EASIEST_AND_HARDEST"),
)
export type SelectionStrategy = Static<typeof SelectionStrategy>

export const TestTestingSettings = Object({
  shortCircuit: Boolean.optional(),
  limit: Number.optional(),
  SelectionStrategy: SelectionStrategy.optional(),
  seed: Number.optional(),
})
export type TestTestingSettings = Static<typeof TestTestingSettings>

export const TestTestingResult = Object({
  correct: Number,
  incorrect: Number,
  total: Number,
  duration: Number,
  succeeded: Boolean,
  shortCircuited: Boolean,
  output: Array(String),
  selectionStrategy: SelectionStrategy.optional(),
  identifiedSolution: Boolean.optional(),
  correctMap: Record(Number, Boolean).optional(),
})
export type TestTestingResult = Static<typeof TestTestingResult>

export const TestTestingCompletedTasks = Object({
  // templateSubmission doesn't complete
  compileSubmission: CompiledSourceResult.optional(),
  checkstyle: CheckstyleResults.optional(),
  ktlint: KtlintResults.optional(),
  // checkCompiledSubmission doesn't complete
  testTesting: TestTestingResult.optional(),
})
export type TestTestingCompletedTasks = Static<typeof TestTestingCompletedTasks>

export const TestTestingFailedTasks = Object({
  checkInitialSubmission: String.optional(),
  templateSubmission: TemplatingFailed.optional(),
  compileSubmission: CompilationFailed.optional(),
  checkstyle: CheckstyleFailed.optional(),
  ktlint: KtlintFailed.optional(),
  checkCompiledSubmission: String.optional(),
  checkExecutedSubmission: String.optional(),
})
export type TestTestingFailedTasks = Static<typeof TestTestingFailedTasks>

export const TestTestResults = Object({
  language: Languages,
  completedSteps: Array(TestTestingStep),
  complete: TestTestingCompletedTasks,
  failedSteps: Array(TestTestingStep),
  failed: TestTestingFailedTasks,
  skippedSteps: Array(TestTestingStep),
  timeout: Boolean,
  lineCountTimeout: Boolean,
  completed: Boolean,
  succeeded: Boolean,
  kind: Literal("TESTTESTING").optional(),
  failedLinting: Boolean.optional(),
})
export type TestTestResults = Static<typeof TestTestResults>
