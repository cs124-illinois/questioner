import { InstanceOf, Partial, Record, Static, String, Unknown } from "runtypes"
import { Languages } from "./languages"

export const UniversalSubmission = Record({
  id: String,
  timestamp: InstanceOf(Date),
  question: Record({
    language: Languages,
    path: String,
  }).And(
    Partial({
      author: String,
      version: String,
    }),
  ),
  author: String,
  contents: String,
  results: Unknown,
}).And(
  Partial({
    email: String,
  }),
)
export type UniversalSubmission = Static<typeof UniversalSubmission>
