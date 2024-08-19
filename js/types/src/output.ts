import { Boolean, Record, Static, String } from "runtypes"

export const TerminalOutput = Record({
  error: Boolean,
  retry: Boolean,
  output: String,
})
export type TerminalOutput = Static<typeof TerminalOutput>
