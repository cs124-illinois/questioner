import { Object, Static, String } from "runtypes"
import { Languages } from "./languages"
import { SubmissionType } from "./submission"

export const QuestionCoordinates = Object({
  language: Languages,
  path: String,
  author: String,
})
export type QuestionCoordinates = Static<typeof QuestionCoordinates>

export const SubmissionCoordinates = QuestionCoordinates.and(
  Object({
    submissionType: SubmissionType,
  }),
)
export type SubmissionCoordinates = Static<typeof SubmissionCoordinates>
