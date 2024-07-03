import { Number, Record, Static } from "runtypes"

export const LineCoverage = Record({
  covered: Number,
  total: Number,
  missed: Number,
})
export type LineCoverage = Static<typeof LineCoverage>
