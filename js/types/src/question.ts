import { Feature, FeatureValue } from "@cs124/jeed-types"
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
import { Languages } from "./languages"
import { LineCounts } from "./linecounts"
import { LineCoverage } from "./linecoverage"

export const QuestionType = Union(Literal("SNIPPET"), Literal("METHOD"), Literal("KLASS"))
export type QuestionType = Static<typeof QuestionType>

export const QuestionPath = Record({
  path: String,
  author: String,
  version: String,
})
export type QuestionPath = Static<typeof QuestionPath>

export const Citation = Record({ source: String }).And(Partial({ link: String }))
export type Citation = Static<typeof Citation>

/*
export const QuestionMetadata = Record({
  unusedFiles: RuntypeArray(String),
}).And(
  Partial({
    focused: Boolean,
    publish: Boolean,
  }),
)
export type QuestionMetadata = Static<typeof QuestionMetadata>
*/

export const MethodInfo = Record({
  className: String,
  methodName: String,
  descriptor: String,
})
export type MethodInfo = Static<typeof MethodInfo>

export const LanguagesResourceUsage = Record({
  java: Number,
}).And(
  Partial({
    kotlin: Number,
  }),
)
export type LanguagesResourceUsage = Static<typeof LanguagesResourceUsage>

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

export const Question = Record({
  published: QuestionPublished,
  classification: QuestionClassification,
}).And(
  Partial({
    validationResults: ValidationResults,
  }),
)
export type Question = Static<typeof Question>

export const QuestionDescription = QuestionPath.And(
  Record({
    name: String,
    description: String,
    packageName: String,
    starter: String,
  }),
)
export type QuestionDescription = Static<typeof QuestionDescription>

export const QuestionTagged = QuestionPublished.And(
  Record({
    tags: Array(String),
  }),
)
export type QuestionTagged = Static<typeof QuestionTagged>

export const questionFeatures = (question: Question) =>
  RuntypeArray(Feature)
    .check(Object.keys(question.classification.featuresByLanguage.java.featureMap))
    .filter((feature) => question.classification.featuresByLanguage.java.featureMap[feature] > 0)
