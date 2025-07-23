import { Boolean, Object, Static, String } from "runtypes"

export const TerminalOutput = Object({
  error: Boolean,
  retry: Boolean,
  output: String,
})
export type TerminalOutput = Static<typeof TerminalOutput>
