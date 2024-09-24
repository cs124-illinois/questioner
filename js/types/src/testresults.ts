import {
  CheckstyleFailed,
  CheckstyleResults,
  CompilationFailed,
  CompiledSourceResult,
  KtlintFailed,
  KtlintResults,
  TemplatingFailed,
} from "@cs124/jeed-types"
import { Array, Boolean, Literal, Number, Partial, Record, Static, String, Union } from "runtypes"
import { Languages } from "./languages"
import { LineCounts } from "./linecounts"
import { LineCoverage } from "./linecoverage"
import { Step } from "./steps"

export const ClassSizeComparison = Record({
  solution: Number,
  submission: Number,
  limit: Number,
  increase: Number,
  failed: Boolean,
})
export type ClassSizeComparison = Static<typeof ClassSizeComparison>

export const ComplexityComparison = Record({
  solution: Number,
  submission: Number,
  limit: Number,
  increase: Number,
  failed: Boolean,
})
export type ComplexityComparison = Static<typeof ComplexityComparison>

export const LineCountComparison = Record({
  solution: LineCounts,
  submission: LineCounts,
  limit: Number,
  allowance: Number,
  increase: Number,
  failed: Boolean,
})
export type LineCountComparison = Static<typeof LineCountComparison>

export const FeaturesComparison = Record({
  errors: Array(String),
  failed: Boolean,
})
export type FeaturesComparison = Static<typeof FeaturesComparison>

export const PassedSteps = Record({
  compiled: Boolean,
  design: Boolean,
  partiallyCorrect: Boolean,
  fullyCorrect: Boolean,
  quality: Boolean,
})
export type PassedSteps = Static<typeof PassedSteps>

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

export const PartialCredit = Record({
  passedSteps: PassedSteps,
}).And(
  Partial({
    passedTestCount: PassedTestCount,
    passedMutantCount: PassedMutantCount,
  }),
)
export type PartialCredit = Static<typeof PartialCredit>

export const RecursionComparison = Record({
  missingMethods: Array(String),
  failed: Boolean,
})
export type RecursionComparison = Static<typeof RecursionComparison>

export const ExecutionCountComparison = Record({
  solution: Number,
  submission: Number,
  limit: Number,
  increase: Number,
  failed: Boolean,
})
export type ExecutionCountComparison = Static<typeof ExecutionCountComparison>

export const MemoryAllocationComparison = Record({
  solution: Number,
  submission: Number,
  limit: Number,
  increase: Number,
  failed: Boolean,
})
export type MemoryAllocationComparison = Static<typeof MemoryAllocationComparison>

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
  differs: Array(
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
  outputAmount: Number,
}).And(
  Partial({
    message: String,
    arguments: String,
    expected: String,
    found: String,
    explanation: String,
    output: String,
    complexity: Number,
    submissionStackTrace: String,
    stdin: String,
    truncatedLines: Number,
  }),
)
export type TestResult = Static<typeof TestResult>

export const TestingResult = Record({
  tests: Array(TestResult),
  testCount: Number,
  completed: Boolean,
  failedReceiverGeneration: Boolean,
  passed: Boolean,
  outputAmount: Number,
  truncatedLines: Number,
})
export type TestingResult = Static<typeof TestingResult>

export const CoverageComparison = Record({
  solution: LineCoverage,
  submission: LineCoverage,
  missed: Array(Number),
  limit: Number,
  increase: Number,
  failed: Boolean,
})
export type CoverageComparison = Static<typeof CoverageComparison>

export const OutputComparison = Record({
  solution: Number,
  submission: Number,
  truncated: Boolean,
  failed: Boolean,
})
export type OutputComparison = Static<typeof OutputComparison>

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
  extraOutput: OutputComparison,
})
export type CompletedTasks = Static<typeof CompletedTasks>

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

export const TestResults = Record({
  language: Languages,
  completedSteps: Array(Step),
  complete: CompletedTasks,
  failedSteps: Array(Step),
  failed: FailedTasks,
  skippedSteps: Array(Step),
  timeout: Boolean,
  lineCountTimeout: Boolean,
  completed: Boolean,
  succeeded: Boolean,
}).And(
  Partial({
    kind: Literal("SOLVE"),
    failedLinting: Boolean,
    failureCount: Number,
  }),
)
export type TestResults = Static<typeof TestResults>
