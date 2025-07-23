import {
  CheckstyleFailed,
  CheckstyleResults,
  CompilationFailed,
  CompiledSourceResult,
  KtlintFailed,
  KtlintResults,
  TemplatingFailed,
} from "@cs124/jeed-types"
import { Array, Boolean, Literal, Number, Object, Static, String, Union } from "runtypes"
import { Languages } from "./languages"
import { LineCounts } from "./linecounts"
import { LineCoverage } from "./linecoverage"
import { Step } from "./steps"

export const ClassSizeComparison = Object({
  solution: Number,
  submission: Number,
  limit: Number,
  increase: Number,
  failed: Boolean,
})
export type ClassSizeComparison = Static<typeof ClassSizeComparison>

export const ComplexityComparison = Object({
  solution: Number,
  submission: Number,
  limit: Number,
  increase: Number,
  failed: Boolean,
})
export type ComplexityComparison = Static<typeof ComplexityComparison>

export const LineCountComparison = Object({
  solution: LineCounts,
  submission: LineCounts,
  limit: Number,
  allowance: Number,
  increase: Number,
  failed: Boolean,
})
export type LineCountComparison = Static<typeof LineCountComparison>

export const FeaturesComparison = Object({
  errors: Array(String),
  failed: Boolean,
})
export type FeaturesComparison = Static<typeof FeaturesComparison>

export const PassedSteps = Object({
  compiled: Boolean,
  design: Boolean,
  partiallyCorrect: Boolean,
  fullyCorrect: Boolean,
  quality: Boolean,
})
export type PassedSteps = Static<typeof PassedSteps>

export const PassedTestCount = Object({
  passed: Number,
  total: Number,
  completed: Boolean,
})
export type PassedTestCount = Static<typeof PassedTestCount>

export const PassedMutantCount = Object({
  passed: Number,
  total: Number,
  completed: Boolean,
})
export type PassedMutantCount = Static<typeof PassedMutantCount>

export const PartialCredit = Object({
  passedSteps: PassedSteps,
  passedTestCount: PassedTestCount.optional(),
  passedMutantCount: PassedMutantCount.optional(),
})
export type PartialCredit = Static<typeof PartialCredit>

export const RecursionComparison = Object({
  missingMethods: Array(String),
  failed: Boolean,
})
export type RecursionComparison = Static<typeof RecursionComparison>

export const ExecutionCountComparison = Object({
  solution: Number,
  submission: Number,
  limit: Number,
  increase: Number,
  failed: Boolean,
})
export type ExecutionCountComparison = Static<typeof ExecutionCountComparison>

export const MemoryAllocationComparison = Object({
  solution: Number,
  submission: Number,
  limit: Number,
  increase: Number,
  failed: Boolean,
})
export type MemoryAllocationComparison = Static<typeof MemoryAllocationComparison>

export const TestResult = Object({
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
  message: String.optional(),
  arguments: String.optional(),
  expected: String.optional(),
  found: String.optional(),
  explanation: String.optional(),
  output: String.optional(),
  complexity: Number.optional(),
  submissionStackTrace: String.optional(),
  stdin: String.optional(),
  truncatedLines: Number.optional(),
})
export type TestResult = Static<typeof TestResult>

export const TestingResult = Object({
  tests: Array(TestResult),
  testCount: Number,
  completed: Boolean,
  failedReceiverGeneration: Boolean,
  passed: Boolean,
  outputAmount: Number,
  truncatedLines: Number,
})
export type TestingResult = Static<typeof TestingResult>

export const CoverageComparison = Object({
  solution: LineCoverage,
  submission: LineCoverage,
  missed: Array(Number),
  limit: Number,
  increase: Number,
  failed: Boolean,
})
export type CoverageComparison = Static<typeof CoverageComparison>

export const OutputComparison = Object({
  solution: Number,
  submission: Number,
  truncated: Boolean,
  failed: Boolean,
})
export type OutputComparison = Static<typeof OutputComparison>

export const CompletedTasks = Object({
  // templateSubmission doesn't complete
  compileSubmission: CompiledSourceResult.optional(),
  checkstyle: CheckstyleResults.optional(),
  ktlint: KtlintResults.optional(),
  // checkCompiledSubmission doesn't complete
  classSize: ClassSizeComparison.optional(),
  complexity: ComplexityComparison.optional(),
  features: FeaturesComparison.optional(),
  lineCount: LineCountComparison.optional(),
  partial: PartialCredit.optional(),
  // execution
  // checkExecutedSubmission doesn't complete
  recursion: RecursionComparison.optional(),
  executionCount: ExecutionCountComparison.optional(),
  memoryAllocation: MemoryAllocationComparison.optional(),
  testing: TestingResult.optional(),
  coverage: CoverageComparison.optional(),
  extraOutput: OutputComparison.optional(),
})
export type CompletedTasks = Static<typeof CompletedTasks>

export const FailedTasks = Object({
  checkInitialSubmission: String.optional(),
  templateSubmission: TemplatingFailed.optional(),
  compileSubmission: CompilationFailed.optional(),
  checkstyle: CheckstyleFailed.optional(),
  ktlint: KtlintFailed.optional(),
  checkCompiledSubmission: String.optional(),
  classSize: String.optional(),
  complexity: String.optional(),
  features: String.optional(),
  lineCount: String.optional(),
  // execution
  checkExecutedSubmission: String.optional(),
  // executionCount doesn't fail
  // memoryAllocation doesn't fail
  // testing doesn't fail
  // coverage doesn't fail
})
export type FailedTasks = Static<typeof FailedTasks>

export const TestResults = Object({
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
  kind: Literal("SOLVE").optional(),
  failedLinting: Boolean.optional(),
  failureCount: Number.optional(),
})
export type TestResults = Static<typeof TestResults>
