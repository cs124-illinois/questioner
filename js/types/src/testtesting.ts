import {
  CheckstyleFailed,
  CheckstyleResults,
  CompilationFailed,
  CompiledSourceResult,
  KtlintFailed,
  KtlintResults,
  TemplatingFailed,
} from "@cs124/jeed-types"
import { Array, Boolean, Dictionary, Literal, Number, Partial, Record, Static, String, Union } from "runtypes"
import { Languages } from "./languages"
import { TestTestingStep } from "./steps"

export const SelectionStrategy = Union(
  Literal("HARDEST"),
  Literal("EASIEST"),
  Literal("EVENLY_SPACED"),
  Literal("EASIEST_AND_HARDEST"),
)
export type SelectionStrategy = Static<typeof SelectionStrategy>

export const TestTestingSettings = Partial({
  shortCircuit: Boolean,
  limit: Number,
  SelectionStrategy: SelectionStrategy,
  seed: Number,
})
export type TestTestingSettings = Static<typeof TestTestingSettings>

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
    selectionStrategy: SelectionStrategy,
    identifiedSolution: Boolean,
    correctMap: Dictionary(Boolean, Number),
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

export const TestTestResults = Record({
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
}).And(
  Partial({
    kind: Literal("TESTTESTING"),
    failedLinting: Boolean,
  }),
)
export type TestTestResults = Static<typeof TestTestResults>
