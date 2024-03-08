import { Literal, Static, Union } from "runtypes"

export const SubmissionType = Union(Literal("SOLVE"), Literal("TESTTESTING"))
export type SubmissionType = Static<typeof SubmissionType>
