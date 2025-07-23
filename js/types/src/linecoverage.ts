import { Number, Object, Static } from "runtypes"

export const LineCoverage = Object({
  covered: Number,
  total: Number,
  missed: Number,
})
export type LineCoverage = Static<typeof LineCoverage>
