import { Number, Record, Static } from "runtypes"

export const LineCounts = Record({
  source: Number,
  comment: Number,
  blank: Number,
})
export type LineCounts = Static<typeof LineCounts>
