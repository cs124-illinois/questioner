import { InstanceOf, Object, Static, String, Unknown } from "runtypes"
import { Languages } from "./languages"
import { SubmissionType } from "./submission"

export const UniversalSubmission = Object({
  type: SubmissionType,
  id: String,
  timestamp: InstanceOf(Date),
  question: Object({
    language: Languages,
    path: String,
    author: String.optional(),
    version: String.optional(),
    contentHash: String.optional(),
  }),
  contents: String,
  results: Unknown,
  email: String.optional(),
  browserID: String.optional(),
})
export type UniversalSubmission = Static<typeof UniversalSubmission>
