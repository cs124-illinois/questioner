import { Number, Object, Static } from "runtypes"

export const LineCounts = Object({
  source: Number,
  comment: Number,
  blank: Number,
})
export type LineCounts = Static<typeof LineCounts>
