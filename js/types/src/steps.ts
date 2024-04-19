import { Literal, Static } from "runtypes"

export const LintingStep = Literal("checkstyle").Or(Literal("ktlint"))
export type LintingStep = Static<typeof LintingStep>
export const LintingSteps: LintingStep[] = ["checkstyle", "ktlint"]

export const QualityStep = Literal("classSize")
  .Or(Literal("complexity"))
  .Or(Literal("features"))
  .Or(Literal("lineCount"))
  .Or(Literal("recursion"))
  .Or(Literal("executioncount"))
  .Or(Literal("memoryAllocation"))
  .Or(Literal("coverage"))
export type QualityStep = Static<typeof QualityStep>
export const QualitySteps: QualityStep[] = [
  "classSize",
  "complexity",
  "features",
  "lineCount",
  "recursion",
  "executioncount",
  "memoryAllocation",
  "coverage",
]

export const CompilationStep = Literal("checkInitialSubmission")
  .Or(Literal("templateSubmission"))
  .Or(Literal("compileSubmission"))
  .Or(Literal("checkCompiledSubmission"))
export type CompilationStep = Static<typeof CompilationStep>
export const CompilationSteps: CompilationStep[] = [
  "checkInitialSubmission",
  "templateSubmission",
  "compileSubmission",
  "checkCompiledSubmission",
]

export const Step = CompilationStep.Or(Literal("partial"))
  .Or(Literal("checkExecutedSubmission"))
  .Or(Literal("testing"))
  .Or(LintingStep)
  .Or(QualityStep)
export type Step = Static<typeof Step>

export const TestTestingStep = CompilationStep.Or(Literal("checkExecutedSubmission").Or(Literal("testTesting"))).Or(
  LintingStep,
)
export type TestTestingStep = Static<typeof TestTestingStep>
