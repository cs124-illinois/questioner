import { SourceLocation } from "@cs124/jeed-types"
import { Step, TerminalOutput, TestResult, TestResults, TestingResult } from "@cs124/questioner-types"
import capitalize from "capitalize"
import indentString from "indent-string"
import phrases from "./phrases"

export const DEFAULT_WARNING_ORDER: Array<Step> = [
  "checkstyle",
  "ktlint",
  "recursion",
  "complexity",
  "features",
  "lineCount",
  "executioncount",
  "memoryAllocation",
  "coverage",
  "classSize",
]

export const simplifyTestResult = (result: TestingResult): TestResult[] => {
  const { passed } = result
  if (passed) {
    return []
  }
  const { tests } = result
  const simplestFailure = tests
    .filter(({ passed, explanation }) => !passed && explanation)
    .sort((a, b) => (a.explanation as string).length - (b.explanation as string).length)[0]

  if (!simplestFailure) {
    return []
  }
  if (simplestFailure.type === "STATIC_METHOD") {
    return [simplestFailure]
  } else {
    return tests.filter((test) => test.runnerID === simplestFailure.runnerID).sort((a, b) => a.stepCount - b.stepCount)
  }
}

export type OutputOptions = {
  successMessage?: string
  showWarnings?: Step[]
  treatAsErrors?: Step[]
  showWithTestResults?: Step[]
  defaultIndentation?: number
}
const DEFAULT_OPTIONS: OutputOptions = {
  successMessage: "Your code passed all tests and code quality checks.",
  defaultIndentation: 2,
}

const pluralize = (input: string, count: number, plural?: string): string => {
  if (count === 1) {
    return input
  } else if (plural) {
    return plural
  } else {
    return `${input}s`
  }
}

export const terminalOutput = (
  results: TestResults,
  contents: string,
  options: OutputOptions = DEFAULT_OPTIONS,
): TerminalOutput => {
  const outputOptions = { ...DEFAULT_OPTIONS, ...options }

  const indentation = outputOptions.defaultIndentation

  const testingCompleted = results.completedSteps.includes("testing")

  if (results.failedSteps.length > 0 || results.timeout) {
    const { failed } = results

    if (results.timeout) {
      return {
        error: true,
        retry: true,
        output: `Testing your submission timed out.\n${indentString(
          `Check for unterminated loops.\nOr your algorithm may be too slow.`,
          indentation,
        )}`,
      }
    } else if (failed.templateSubmission) {
      return { error: true, retry: true, output: `Templating failed. Please report a bug.` }
    } else if (failed.checkstyle) {
      const output =
        failed.checkstyle?.errors
          .map(({ location: { source, line }, message }) => {
            return `${source === "" ? "Line " : `${source}:`}${line}: checkstyle error: ${message}`
          })
          .join("\n") || ""
      const errorCount = Object.keys(failed.checkstyle?.errors || {}).length
      return { error: true, retry: false, output: `${output}\n${errorCount} error${errorCount > 1 ? "s" : ""}` }
    } else if (failed.ktlint) {
      const output =
        failed.ktlint?.errors
          .map(({ location: { source, line }, detail }) => {
            return `${source === "" ? "Line " : `${source}:`}${line}: ktlint error: ${detail}`
          })
          .join("\n") || ""
      const errorCount = Object.keys(failed.ktlint?.errors || {}).length
      return { error: true, retry: false, output: `${output}\n${errorCount} error${errorCount > 1 ? "s" : ""}` }
    } else if (failed.compileSubmission) {
      const output =
        failed.compileSubmission.errors
          .map((error) => {
            const { location, message } = error
            if (location) {
              const { source, line, column } = location as SourceLocation
              const originalLine = contents.split("\n")[line - 1]

              const firstErrorLine = message.split("\n").slice(0, 1).join()
              const restOfError = message
                .split("\n")
                .slice(1)
                .filter((line) => {
                  return !(source === "" && line.trim().startsWith("location: class"))
                })
                .join("\n")
              return `${source === "" ? "Line " : `${source}:`}${line}: error: ${firstErrorLine}\n${
                originalLine ? originalLine + "\n" + new Array(column >= 0 ? column : 0).join(" ") + "^" : ""
              }${restOfError ? "\n" + restOfError : ""}`
            } else {
              return message
            }
          })
          .join("\n") || ""
      const errorCount = Object.keys(failed.compileSubmission.errors).length
      return { error: true, retry: false, output: `${output}\n${errorCount} error${errorCount > 1 ? "s" : ""}` }
    } else if (failed.checkInitialSubmission) {
      return {
        error: true,
        retry: false,
        output: `Your submission had errors:\n${indentString(failed.checkInitialSubmission, indentation)}`,
      }
    } else if (
      failed.checkCompiledSubmission ||
      failed.checkExecutedSubmission ||
      failed.features ||
      failed.classSize
    ) {
      return {
        error: true,
        retry: false,
        output: `Your submission had errors:\n${indentString(
          (failed.checkCompiledSubmission ||
            failed.checkExecutedSubmission ||
            failed.features ||
            failed.classSize) as string,
          indentation,
        )}`,
      }
    } else if (failed.complexity) {
      return {
        error: true,
        retry: false,
        output: `Your submission was too complex to test:\n${indentString(failed.complexity, indentation)}`,
      }
    } else if (failed.lineCount) {
      return {
        error: true,
        retry: false,
        output: `Your submission was too long to test:\n${indentString(failed.lineCount, indentation)}`,
      }
    } else if (!testingCompleted) {
      return {
        error: true,
        retry: true,
        output: `Invalid testing result. If this happens repeatedly, please report a bug.`,
      }
    }
  }

  if (!results.complete.testing) {
    return {
      error: true,
      retry: true,
      output: `Didn't return a testing result. If this happens repeatedly, please report a bug.`,
    }
  }

  const { complete, completedSteps, failedLinting } = results
  const warnings: { [key in Step]?: string } = {}
  if (failedLinting === true) {
    if (completedSteps.includes("checkstyle")) {
      const output =
        complete.checkstyle?.errors
          .map(({ location: { source, line }, message }) => {
            return `${source === "" ? "  Line " : `  ${source}:`}${line}: checkstyle error: ${message}`
          })
          .join("\n") || ""
      const errorCount = Object.keys(complete.checkstyle?.errors || {}).length
      warnings["checkstyle"] = `Your submission has checkstyle errors:\n${output}\n${errorCount} error${
        errorCount > 1 ? "s" : ""
      }`
    } else if (completedSteps.includes("ktlint")) {
      const output =
        complete.ktlint?.errors
          .map(({ location: { source, line }, detail }) => {
            return `${source === "" ? "  Line " : `  ${source}:`}${line}: ktlint error: ${detail}`
          })
          .join("\n") || ""
      const errorCount = Object.keys(complete.ktlint?.errors || {}).length
      warnings["ktlint"] = `Your submission has ktlint errors:\n${output}\n${errorCount} error${
        errorCount > 1 ? "s" : ""
      }`
    }
  }
  if (complete.complexity?.failed === true) {
    const { solution, submission } = complete.complexity
    warnings["complexity"] = `Your submission is a bit too complicated:\n${indentString(
      `The solution has ${solution} code ${pluralize("path", solution)}.\nYour submission has ${submission}.`,
      indentation,
    )}`
  }
  if (complete.classSize?.failed === true) {
    const { solution, submission } = complete.classSize
    warnings["classSize"] = `Your submission class is too large:\n${indentString(
      `The solution is ${pluralize("byte", solution)} bytes.\nYour submission is ${submission}.`,
      indentation,
    )}`
  }
  if (complete.features?.failed === true) {
    const { errors } = complete.features
    warnings["features"] = `Your submission generated design warnings:\n${indentString(errors.join("\n"), indentation)}`
  }
  if (complete.lineCount?.failed === true) {
    const { solution, submission } = complete.lineCount
    warnings["lineCount"] = `Your submission is too long:\n${indentString(
      `The solution has ${solution.source} source ${pluralize("line", solution.source)}.\nYour submission has ${
        submission.source
      }.`,
      indentation,
    )}`
  }
  if (complete.coverage?.failed === true) {
    warnings["coverage"] = `Your submission contains unexecuted code:\n${indentString(
      `You have ${complete.coverage.increase} more lines of unexecuted code than the solution.\nWhich exceeds the limit of ${complete.coverage.limit}.`,
      indentation,
    )}`
  }
  if (complete.executionCount?.failed === true) {
    const { solution, submission } = complete.executionCount
    warnings["executioncount"] = `Your submission is too inefficient:\n${indentString(
      `The solution executed ${solution} ${pluralize(
        "line",
        solution,
      )} to complete the tests.\nYour submission took ${submission} (${Math.round((submission / solution) * 100)}%).`,
      indentation,
    )}`
  }
  if (complete.memoryAllocation?.failed === true) {
    const { solution, submission } = complete.memoryAllocation
    warnings["memoryAllocation"] = `Your submission uses too much memory:\n${indentString(
      `The solution allocated ${solution} bytes to complete the tests.\nYour submission needed ${submission} (${Math.round(
        (submission / solution) * 100,
      )}%).`,
      indentation,
    )}`
  }
  if (complete.recursion?.failed === true) {
    warnings["recursion"] =
      `Your submission did not implement required recursive methods recursively: ${complete.recursion.missingMethods.join(
        ", ",
      )}`
  }
  if (results.succeeded !== true) {
    if (results.failureCount === undefined) {
      return {
        error: true,
        retry: true,
        output: "Error printing testing output. If this happens repeatedly, please report a bug.",
      }
    }
    const { testing } = results.complete
    const failures = simplifyTestResult(testing)
    const lastFailure = failures[failures.length - 1]

    let message = `Ran ${testing.testCount} test${testing.testCount > 1 ? "s" : ""}. Stopped after finding ${
      results.failureCount
    } failure${results.failureCount > 1 ? "s" : ""}${results.failureCount > 1 ? `. Here is one:` : ":"}\n\n${
      lastFailure.explanation
    }`

    if (lastFailure.submissionStackTrace) {
      message += `\n\nSubmission exception stack trace:\n${indentString(lastFailure.submissionStackTrace, indentation)}`
    }

    const output = failures
      .map((test) => test.output)
      .filter((output) => output !== "")
      .join("")
      .trim()

    const allOutputFailures =
      lastFailure.differs &&
      lastFailure.differs.filter((differ) => ["STDOUT", "STDERR", "INTERLEAVED_OUTPUT"].includes(differ)).length === 0

    if (output && (!lastFailure.differs || allOutputFailures)) {
      message += `\n\nTesting output:\n${output}`
    }

    if (failures.length > 1) {
      message += `\n\nTesting method sequence:\n${indentString(
        failures.map((test) => test.methodCall).join("\n"),
        indentation,
      )}`
    }

    warnings["testing"] = message
  }

  const { showWarnings, treatAsErrors, showWithTestResults } = outputOptions

  if (showWarnings) {
    for (const warning of Object.keys(warnings) as Step[]) {
      if (!showWarnings.includes(warning)) {
        delete warnings[warning]
      }
    }
  }

  const wellDone = capitalize(phrases[Math.floor(Math.random() * phrases.length)])
  if (Object.keys(warnings).length === 0) {
    return { error: false, retry: false, output: `${outputOptions.successMessage} ${wellDone}!` }
  } else if (warnings["testing"]) {
    let header
    for (const which of DEFAULT_WARNING_ORDER) {
      if (warnings[which] && showWithTestResults && showWithTestResults.includes(which)) {
        header = `Your submission will eventually be considered incorrect because of this error:\n${indentString(
          warnings[which] as string,
          indentation,
        )}`
      }
    }
    if (header) {
      return { error: false, retry: false, output: `${header}\n---\n${warnings["testing"]}` }
    } else {
      return { error: false, retry: false, output: warnings["testing"] }
    }
  } else {
    for (const which of DEFAULT_WARNING_ORDER) {
      if (warnings[which]) {
        if (treatAsErrors && treatAsErrors.includes(which)) {
          return {
            error: false,
            retry: false,
            output: `Your code passed the tests, but was considered incorrect because of this error:\n${indentString(
              warnings[which] as string,
              indentation,
            )}`,
          }
        } else {
          return {
            error: false,
            retry: false,
            output: `Your code passed all the tests! ${wellDone}!\n\nBut we noticed something you could improve:\n${indentString(
              warnings[which] as string,
              indentation,
            )}`,
          }
        }
      }
    }
  }

  return {
    error: true,
    retry: true,
    output: "Error printing testing output. If this happens repeatedly, please report a bug.",
  }
}
