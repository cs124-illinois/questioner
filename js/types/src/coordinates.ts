import { Record, Static, String } from "runtypes"
import { Languages } from "./languages"
import { SubmissionType } from "./submission"

export const QuestionCoordinates = Record({
  language: Languages,
  path: String,
  author: String,
})
export type QuestionCoordinates = Static<typeof QuestionCoordinates>

export const SubmissionCoordinates = QuestionCoordinates.And(
  Record({
    submissionType: SubmissionType,
  }),
)
export type SubmissionCoordinates = Static<typeof SubmissionCoordinates>
