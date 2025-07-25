import { Literal, Partial, Record, Static, String, Union } from "runtypes"
import { Languages } from "./languages"
import { TestTestingSettings } from "./testtesting"

export const SubmissionType = Union(Literal("SOLVE"), Literal("TESTTESTING"))
export type SubmissionType = Static<typeof SubmissionType>

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
