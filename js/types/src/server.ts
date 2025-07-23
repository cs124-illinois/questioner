import { Boolean, Number, Object, Static, String } from "runtypes"
import { SubmissionType } from "./submission"
import { TestResults } from "./testresults"
import { TestTestResults } from "./testtesting"

export const CacheStats = Object({
  hits: Number,
  misses: Number,
})
export type CacheStats = Static<typeof CacheStats>

export const ServerResponse = Object({
  type: SubmissionType,
  canCache: Boolean,
  cacheStats: CacheStats,
  duration: Number,
  version: String,
  solveResults: TestResults.optional(),
  testTestingResults: TestTestResults.optional(),
})
export type ServerResponse = Static<typeof ServerResponse>
