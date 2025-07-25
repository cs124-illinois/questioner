import { InstanceOf, Partial, Record, Static, String, Unknown } from "runtypes"
import { Languages } from "./languages"
import { SubmissionType } from "./submission"

export const UniversalSubmission = Record({
  type: SubmissionType,
  id: String,
  timestamp: InstanceOf(Date),
  question: Record({
    language: Languages,
    path: String,
  }).And(
    Partial({
      author: String,
      version: String,
      contentHash: String,
    }),
  ),
  contents: String,
  results: Unknown,
}).And(
  Partial({
    email: String,
    browserID: String,
  }),
)
export type UniversalSubmission = Static<typeof UniversalSubmission>
