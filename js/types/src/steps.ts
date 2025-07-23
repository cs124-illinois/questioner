import { Literal, Static } from "runtypes"

export const LintingStep = Literal("checkstyle").or(Literal("ktlint"))
export type LintingStep = Static<typeof LintingStep>
export const LintingSteps: LintingStep[] = ["checkstyle", "ktlint"]

export const QualityStep = Literal("classSize")
  .or(Literal("complexity"))
  .or(Literal("features"))
  .or(Literal("lineCount"))
  .or(Literal("recursion"))
  .or(Literal("executioncount"))
  .or(Literal("memoryAllocation"))
  .or(Literal("coverage"))
  .or(Literal("extraOutput"))

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
  "extraOutput",
]

export const CompilationStep = Literal("checkInitialSubmission")
  .or(Literal("templateSubmission"))
  .or(Literal("compileSubmission"))
  .or(Literal("checkCompiledSubmission"))
export type CompilationStep = Static<typeof CompilationStep>
export const CompilationSteps: CompilationStep[] = [
  "checkInitialSubmission",
  "templateSubmission",
  "compileSubmission",
  "checkCompiledSubmission",
]

export const Step = CompilationStep.or(Literal("partial"))
  .or(Literal("checkExecutedSubmission"))
  .or(Literal("testing"))
  .or(LintingStep)
  .or(QualityStep)
export type Step = Static<typeof Step>

export const TestTestingStep = CompilationStep.or(Literal("checkExecutedSubmission").or(Literal("testTesting"))).or(
  LintingStep,
)
export type TestTestingStep = Static<typeof TestTestingStep>

export const stepToKey = (step: Step): string => {
  if (step === "executioncount") {
    return "executionCount"
  } else {
    return step
  }
}

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
