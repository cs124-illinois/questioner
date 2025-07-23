import { Literal, Object, Static, String, Union } from "runtypes"
import { Languages } from "./languages"
import { TestTestingSettings } from "./testtesting"

export const SubmissionType = Union(Literal("SOLVE"), Literal("TESTTESTING"))
export type SubmissionType = Static<typeof SubmissionType>

export const Submission = Object({
  type: SubmissionType,
  contentHash: String,
  language: Languages,
  contents: String,
  testTestingSettings: TestTestingSettings.optional(),
})
export type Submission = Static<typeof Submission>
