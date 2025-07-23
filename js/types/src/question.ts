import { FeatureValue } from "@cs124/jeed-types"
import { Array, Boolean, Literal, Number, Object, Record, Static, String, Union } from "runtypes"
import { Languages } from "./languages"
import { LineCounts } from "./linecounts"
import { LineCoverage } from "./linecoverage"

export const QuestionType = Union(Literal("SNIPPET"), Literal("METHOD"), Literal("KLASS"))
export type QuestionType = Static<typeof QuestionType>

export const QuestionPath = Object({
  path: String,
  author: String,
  version: String,
})
export type QuestionPath = Static<typeof QuestionPath>

export const Citation = Object({
  source: String,
  link: String.optional(),
})
export type Citation = Static<typeof Citation>

export const MethodInfo = Object({
  className: String,
  methodName: String,
  descriptor: String,
})
export type MethodInfo = Static<typeof MethodInfo>

export const LanguagesResourceUsage = Object({
  java: Number,
  kotlin: Number.optional(),
})
export type LanguagesResourceUsage = Static<typeof LanguagesResourceUsage>

export const QuestionPublished = QuestionPath.and(
  Object({
    contentHash: String,
    name: String,
    type: QuestionType,
    packageName: String,
    languages: Array(Languages),
    descriptions: Record(Languages, String),
    templateImports: Array(String),
    questionerVersion: String,
    authorName: String,
    klass: String,
    citation: Citation.optional(),
    starters: Record(Languages, String).optional(),
    tags: Array(String).optional(),
    kotlinImports: Array(String).optional(),
    javaTestingImports: Array(String).optional(),
    kotlinTestingImports: Array(String).optional(),
  }),
)
export type QuestionPublished = Static<typeof QuestionPublished>

export const QuestionClassification = Object({
  featuresByLanguage: Record(Languages, FeatureValue),
  complexity: Record(Languages, Number),
  lineCounts: Record(Languages, LineCounts),
  recursiveMethodsByLanguage: Record(Languages, Array(MethodInfo)).optional(),
  loadedClassesByLanguage: Record(Languages, Array(String)).optional(),
})
export type QuestionClassification = Static<typeof QuestionClassification>

export const ValidationResults = Object({
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
  solutionMaxClassSize: LanguagesResourceUsage.optional(),
  canTestTest: Boolean.optional(),
})
export type ValidationResults = Static<typeof ValidationResults>

export const Question = Object({
  published: QuestionPublished,
  classification: QuestionClassification,
  validationResults: ValidationResults.optional(),
})
export type Question = Static<typeof Question>

export const QuestionDescription = QuestionPath.and(
  Object({
    name: String,
    description: String,
    packageName: String,
    starter: String,
  }),
)
export type QuestionDescription = Static<typeof QuestionDescription>

export const QuestionTagged = QuestionPublished.and(
  Object({
    tags: Array(String),
  }),
)
export type QuestionTagged = Static<typeof QuestionTagged>

export const questionFeatures = (question: Question): string[] => {
  const globalObject = globalThis as typeof globalThis & { Object: ObjectConstructor }
  return globalObject.Object.keys(question.classification.featuresByLanguage.java.featureMap).filter(
    (feature: string) => question.classification.featuresByLanguage.java.featureMap[feature] > 0,
  )
}
