import { SourceLocation } from "@cs124/jeed-types"
import { Step, TestingResult, TestResult, TestResults } from "@cs124/questioner-types"
import capitalize from "capitalize"
import indentString from "indent-string"
import phrases from "./phrases"

export const DEFAULT_WARNING_ORDER: Array<Step> = [
  "checkstyle",
  "ktlint",
  "complexity",
  "features",
  "lineCount",
  "executioncount",
  "memoryAllocation",
  "coverage",
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
  successMessage: string
  showWarnings?: Step[]
  treatAsErrors?: Step[]
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
  options: OutputOptions = DEFAULT_OPTIONS
): string => {
  const outputOptions = { ...DEFAULT_OPTIONS, ...options }

  const indentation = outputOptions.defaultIndentation

  const testingCompleted = results.completedSteps.includes("testing")

  if (results.failedSteps.length > 0 || results.timeout) {
    const { failed } = results

    if (results.timeout) {
      return `Testing your submission timed out.\n${indentString(
        `Check for unterminated loops.\nOr your algorithm may be too slow.`,
        indentation
      )}`
    } else if (failed.templateSubmission) {
      return `Templating failed. Please report a bug.`
    } else if (failed.checkstyle) {
      const output =
        failed.checkstyle?.errors
          .map(({ location: { source, line }, message }) => {
            return `${source === "" ? "Line " : `${source}:`}${line}: checkstyle error: ${message}`
          })
          .join("\n") || ""
      const errorCount = Object.keys(failed.checkstyle?.errors || {}).length
      return `${output}\n${errorCount} error${errorCount > 1 ? "s" : ""}`
    } else if (failed.ktlint) {
      const output =
        failed.ktlint?.errors
          .map(({ location: { source, line }, detail }) => {
            return `${source === "" ? "Line " : `${source}:`}${line}: ktlint error: ${detail}`
          })
          .join("\n") || ""
      const errorCount = Object.keys(failed.ktlint?.errors || {}).length
      return `${output}\n${errorCount} error${errorCount > 1 ? "s" : ""}`
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
      return `${output}\n${errorCount} error${errorCount > 1 ? "s" : ""}`
    } else if (failed.checkInitialSubmission) {
      return `Your submission had errors:\n${indentString(failed.checkInitialSubmission, indentation)}`
    } else if (failed.checkCompiledSubmission || failed.checkExecutedSubmission || failed.features) {
      return `Your submission had errors:\n${indentString(
        (failed.checkCompiledSubmission || failed.checkExecutedSubmission || failed.features) as string,
        indentation
      )}`
    } else if (failed.complexity) {
      return `Your submission was too complex to test:\n${indentString(failed.complexity, indentation)}`
    } else if (failed.lineCount) {
      return `Your submission was too long to test:\n${indentString(failed.lineCount, indentation)}`
    } else if (!testingCompleted) {
      return `Invalid testing result. If this happens repeatedly, please report a bug.`
    }
  }

  if (!results.complete.testing) {
    return `Didn't return a testing result. If this happens repeatedly, please report a bug.`
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
      indentation
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
      indentation
    )}`
  }
  if (complete.coverage?.failed === true) {
    warnings["coverage"] = `Your submission contains unexecuted code:\n${indentString(
      `You have ${complete.coverage.increase} more lines of unexecuted code than the solution.\nWhich exceeds the limit of ${complete.coverage.limit}.`,
      indentation
    )}`
  }
  if (complete.executionCount?.failed === true) {
    const { solution, submission } = complete.executionCount
    warnings["executioncount"] = `Your submission is too inefficient:\n${indentString(
      `The solution executed ${solution} ${pluralize(
        "line",
        solution
      )} to complete the tests.\nYour submission took ${submission} (${Math.round((submission / solution) * 100)}%).`,
      indentation
    )}`
  }
  if (complete.memoryAllocation?.failed === true) {
    const { solution, submission } = complete.memoryAllocation
    warnings["executioncount"] = `Your submission uses too much memory:\n${indentString(
      `The solution allocated ${solution} bytes to complete the tests.\nYour submission needed ${submission} (${Math.round(
        (submission / solution) * 100
      )}%).`,
      indentation
    )}`
  }
  if (results.succeeded !== true) {
    if (results.failureCount === undefined) {
      return "Error printing testing output. If this happens repeatedly, please report a bug."
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
        indentation
      )}`
    }

    warnings["testing"] = message
  }

  const { showWarnings, treatAsErrors } = outputOptions

  if (showWarnings) {
    for (const warning of Object.keys(warnings) as Step[]) {
      if (!showWarnings.includes(warning)) {
        delete warnings[warning]
      }
    }
  }

  const wellDone = capitalize(phrases[Math.floor(Math.random() * phrases.length)])
  if (Object.keys(warnings).length === 0) {
    return `${outputOptions.successMessage} ${wellDone}!`
  } else if (warnings["testing"]) {
    return warnings["testing"]
  } else {
    for (const which of DEFAULT_WARNING_ORDER) {
      if (warnings[which]) {
        if (treatAsErrors && treatAsErrors.includes(which)) {
          return `Your code the tests, but was considered incorrect because of this error:\n${indentString(
            warnings[which] as string,
            indentation
          )}`
        } else {
          return `Your code passed all the tests! ${wellDone}!\n\nBut we noticed something you could improve:\n${indentString(
            warnings[which] as string,
            indentation
          )}`
        }
      }
    }
  }

  return "Error printing testing output. If this happens repeatedly, please report a bug."
}