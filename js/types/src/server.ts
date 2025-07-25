import { Boolean, Number, Partial, Record, Static, String } from "runtypes"
import { SubmissionType } from "./submission"
import { TestResults } from "./testresults"
import { TestTestResults } from "./testtesting"

export const CacheStats = Record({
  hits: Number,
  misses: Number,
})
export type CacheStats = Static<typeof CacheStats>

export const ServerResponse = Record({
  type: SubmissionType,
  canCache: Boolean,
  cacheStats: CacheStats,
  duration: Number,
  version: String,
}).And(
  Partial({
    solveResults: TestResults,
    testTestingResults: TestTestResults,
  }),
)
export type ServerResponse = Static<typeof ServerResponse>
