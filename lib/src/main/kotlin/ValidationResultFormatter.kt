@file:Suppress("unused")

package edu.illinois.cs.cs125.questioner.lib

import org.apache.commons.text.StringEscapeUtils

/**
 * Formats ValidationResult instances as HTML reports.
 */
object ValidationResultFormatter {
    private const val CSS_STYLES = """
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            background: #1a1a2e;
            color: #eee;
            line-height: 1.6;
            padding: 2rem;
        }
        .container { max-width: 1000px; margin: 0 auto; }
        header { margin-bottom: 2rem; }
        h1 { font-size: 2rem; font-weight: 600; }
        h2 { font-size: 1.4rem; margin: 2rem 0 1rem; color: #94a3b8; }
        h3 { font-size: 1.2rem; margin: 1.5rem 0 0.75rem; color: #64748b; }
        .timestamp { color: #888; font-size: 0.9rem; margin-top: 0.5rem; }
        .badge {
            display: inline-block;
            padding: 0.25rem 0.75rem;
            border-radius: 9999px;
            font-size: 0.75rem;
            font-weight: 600;
            text-transform: uppercase;
            margin-right: 0.5rem;
        }
        .badge-success { background: #166534; color: #4ade80; }
        .badge-failure { background: #991b1b; color: #fca5a5; }
        .badge-phase { background: #1e40af; color: #93c5fd; }
        .badge-language { background: #0f766e; color: #5eead4; }
        .summary {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
            gap: 1rem;
            margin: 1.5rem 0;
        }
        .stat {
            background: #16213e;
            padding: 1rem 1.5rem;
            border-radius: 12px;
            text-align: center;
        }
        .stat-value { font-size: 1.75rem; font-weight: 700; }
        .stat-label { color: #888; font-size: 0.8rem; text-transform: uppercase; }
        .stat.success .stat-value { color: #4ade80; }
        .stat.failure .stat-value { color: #ff6b6b; }
        .alert {
            padding: 1rem 1.5rem;
            border-radius: 8px;
            margin: 1rem 0;
        }
        .alert-success {
            background: #052e16;
            border-left: 4px solid #4ade80;
            color: #86efac;
        }
        .alert-error {
            background: #450a0a;
            border-left: 4px solid #f87171;
            color: #fca5a5;
        }
        .alert-warning {
            background: #422006;
            border-left: 4px solid #fbbf24;
            color: #fde68a;
        }
        .code-block {
            background: #0f172a;
            border-radius: 8px;
            overflow: hidden;
            margin: 1rem 0;
        }
        .code-block-header {
            background: #1e293b;
            padding: 0.5rem 1rem;
            font-size: 0.85rem;
            color: #94a3b8;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .code-block pre {
            padding: 1rem;
            margin: 0;
            overflow-x: auto;
            font-size: 0.85rem;
            line-height: 1.5;
        }
        .code-block code {
            font-family: 'JetBrains Mono', 'Fira Code', Monaco, Consolas, monospace;
        }
        .error-details {
            background: #16213e;
            border-radius: 8px;
            overflow: hidden;
            margin: 1rem 0;
        }
        .error-header {
            padding: 1rem 1.5rem;
            display: flex;
            align-items: center;
            gap: 1rem;
            border-bottom: 1px solid #2a3a5e;
        }
        .error-type {
            font-weight: 600;
            font-size: 1.1rem;
        }
        .error-body {
            padding: 1.5rem;
            background: #0f1729;
        }
        .error-message {
            background: #1a2744;
            padding: 1rem;
            border-radius: 8px;
            margin-bottom: 1rem;
            white-space: pre-wrap;
            word-break: break-word;
        }
        .suggestion {
            background: #1a2744;
            padding: 1rem;
            border-radius: 8px;
            margin-top: 1rem;
            border-left: 4px solid #3b82f6;
        }
        .suggestion-title {
            color: #60a5fa;
            font-weight: 600;
            margin-bottom: 0.5rem;
        }
        ul {
            margin-left: 1.5rem;
            margin-top: 0.5rem;
        }
        li { margin: 0.25rem 0; }
        .testing-sequence {
            background: #16213e;
            border-radius: 8px;
            padding: 1rem;
            margin: 1rem 0;
        }
        .testing-sequence-title {
            color: #94a3b8;
            font-size: 0.9rem;
            margin-bottom: 0.5rem;
        }
        .testing-sequence pre {
            background: #0f172a;
            padding: 1rem;
            border-radius: 6px;
            overflow-x: auto;
            font-size: 0.8rem;
        }
        code.inline {
            background: #1e293b;
            padding: 0.2rem 0.5rem;
            border-radius: 4px;
            font-size: 0.85em;
        }
        .section {
            margin: 2rem 0;
            padding: 1.5rem;
            background: #16213e;
            border-radius: 12px;
        }
        .file-path {
            font-family: monospace;
            font-size: 0.85rem;
            color: #64748b;
            margin-top: 0.25rem;
        }
    """

    /**
     * Generate HTML document wrapper with common styling.
     */
    private fun wrapDocument(title: String, body: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>$title</title>
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/atom-one-dark.min.css">
            <style>
            $CSS_STYLES
            </style>
        </head>
        <body>
            <div class="container">
            $body
            </div>
            <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
            <script>hljs.highlightAll();</script>
        </body>
        </html>
    """.trimIndent()

    /**
     * Format a ValidationResult as an HTML report.
     */
    fun formatHtml(result: ValidationResult): String = when (result) {
        is ValidationResult.Success -> formatSuccessHtml(result)
        is ValidationResult.Failure -> formatFailureHtml(result)
    }

    /**
     * Format a successful validation result as HTML.
     */
    private fun formatSuccessHtml(result: ValidationResult.Success): String {
        val summary = result.summary
        val phaseName = if (result.phase == ValidationPhase.VALIDATE) "Validation" else "Calibration"

        val body = buildString {
            appendLine("<header>")
            appendLine("  <h1>${escapeHtml(result.questionName)}</h1>")
            appendLine("  <div>")
            appendLine("    <span class=\"badge badge-success\">Passed</span>")
            appendLine("    <span class=\"badge badge-phase\">$phaseName</span>")
            if (summary.hasKotlin) {
                appendLine("    <span class=\"badge badge-language\">Kotlin</span>")
            }
            appendLine("  </div>")
            appendLine("  <p class=\"file-path\">${escapeHtml(result.questionPath)}</p>")
            appendLine("  <p class=\"timestamp\">Completed in ${result.durationMs}ms</p>")
            appendLine("</header>")

            appendLine("<div class=\"alert alert-success\">$phaseName completed successfully.</div>")

            appendLine("<div class=\"summary\">")
            appendLine("  <div class=\"stat success\">")
            appendLine("    <div class=\"stat-value\">${summary.testCount}</div>")
            appendLine("    <div class=\"stat-label\">Tests Run</div>")
            appendLine("  </div>")
            appendLine("  <div class=\"stat\">")
            appendLine("    <div class=\"stat-value\">${summary.requiredTestCount}</div>")
            appendLine("    <div class=\"stat-label\">Required Tests</div>")
            appendLine("  </div>")
            if (summary.mutationCount > 0) {
                appendLine("  <div class=\"stat\">")
                appendLine("    <div class=\"stat-value\">${summary.mutationCount}</div>")
                appendLine("    <div class=\"stat-label\">Mutations</div>")
                appendLine("  </div>")
            }
            appendLine("  <div class=\"stat\">")
            appendLine("    <div class=\"stat-value\">${summary.retries}</div>")
            appendLine("    <div class=\"stat-label\">Retries</div>")
            appendLine("  </div>")
            appendLine("  <div class=\"stat\">")
            appendLine("    <div class=\"stat-value\">${summary.requiredTime}ms</div>")
            appendLine("    <div class=\"stat-label\">Required Time</div>")
            appendLine("  </div>")
            appendLine("</div>")

            summary.testingSequence?.let { sequence ->
                if (sequence.isNotEmpty()) {
                    appendLine("<div class=\"testing-sequence\">")
                    appendLine("  <div class=\"testing-sequence-title\">Testing Sequence</div>")
                    appendLine("  <pre><code class=\"language-text\">${escapeHtml(sequence.joinToString("\n"))}</code></pre>")
                    appendLine("</div>")
                }
            }
        }

        return wrapDocument("${result.questionName} - $phaseName Passed", body)
    }

    /**
     * Format a failed validation result as HTML.
     */
    private fun formatFailureHtml(result: ValidationResult.Failure): String {
        val phaseName = if (result.phase == ValidationPhase.VALIDATE) "Validation" else "Calibration"

        val body = buildString {
            appendLine("<header>")
            appendLine("  <h1>${escapeHtml(result.questionName)}</h1>")
            appendLine("  <div>")
            appendLine("    <span class=\"badge badge-failure\">Failed</span>")
            appendLine("    <span class=\"badge badge-phase\">$phaseName</span>")
            appendLine("  </div>")
            appendLine("  <p class=\"file-path\">${escapeHtml(result.questionPath)}</p>")
            appendLine("  <p class=\"timestamp\">Failed after ${result.durationMs}ms</p>")
            appendLine("</header>")

            appendLine("<div class=\"alert alert-error\">$phaseName failed. See details below.</div>")

            appendLine("<div class=\"error-details\">")
            appendLine("  <div class=\"error-header\">")
            appendLine("    <span class=\"error-type\">${escapeHtml(result.error.errorType)}</span>")
            appendLine("  </div>")
            appendLine("  <div class=\"error-body\">")
            appendLine("    <div class=\"error-message\">${escapeHtml(result.error.message)}</div>")
            append(formatErrorDetails(result.error))
            appendLine("  </div>")
            appendLine("</div>")

            result.error.testingSequence?.let { sequence ->
                if (sequence.isNotEmpty()) {
                    appendLine("<div class=\"testing-sequence\">")
                    appendLine("  <div class=\"testing-sequence-title\">Testing Sequence at Time of Failure</div>")
                    appendLine("  <pre><code class=\"language-text\">${escapeHtml(sequence.joinToString("\n"))}</code></pre>")
                    appendLine("</div>")
                }
            }
        }

        return wrapDocument("${result.questionName} - $phaseName Failed", body)
    }

    /**
     * Format error-specific details (code blocks, suggestions, etc.).
     */
    private fun formatErrorDetails(error: ValidationError): String = buildString {
        when (error) {
            is ValidationError.SolutionFailed -> {
                appendCodeBlock(error.solutionCode, error.solutionLanguage, "Solution Code", error.solutionPath)
                appendSuggestion(
                    "Possible Fixes",
                    listOf("Verify that this solution matches the reference solution"),
                )
            }

            is ValidationError.SolutionReceiverGeneration -> {
                appendCodeBlock(error.solutionCode, error.solutionLanguage, "Solution Code", error.solutionPath)
                appendSuggestion(
                    "Possible Fixes",
                    listOf(
                        "Examine any @FilterParameters methods you might be using",
                        "Check for exceptions thrown in your constructor",
                        "Consider adding parameter generation methods for your constructor",
                    ),
                )
            }

            is ValidationError.SolutionFailedLinting -> {
                appendCodeBlock(error.solutionCode, error.solutionLanguage, "Solution Code", error.solutionPath)
                appendLine("<h3>Linting Errors</h3>")
                appendLine("<pre class=\"error-message\">${escapeHtml(error.lintingErrors)}</pre>")
            }

            is ValidationError.SolutionThrew -> {
                appendCodeBlock(error.solutionCode, error.solutionLanguage, "Solution Code", error.solutionPath)
                appendLine("<h3>Exception Details</h3>")
                appendLine("<p>Threw: <code class=\"inline\">${escapeHtml(error.thrownException)}</code></p>")
                appendLine("<p>Parameters: <code class=\"inline\">${escapeHtml(error.parameters)}</code></p>")
                appendSuggestion(
                    "Possible Fixes",
                    listOf(
                        "If it should throw, allow it using <code>@Correct(solutionThrows = true)</code>",
                        "Otherwise filter the inputs using <code>@FixedParameters</code>, <code>@RandomParameters</code>, or <code>@FilterParameters</code>",
                    ),
                )
            }

            is ValidationError.SolutionTestingThrew -> {
                appendCodeBlock(error.solutionCode, error.solutionLanguage, "Solution Code", error.solutionPath)
                appendLine("<h3>Exception Details</h3>")
                appendLine("<p>Threw: <code class=\"inline\">${escapeHtml(error.thrownException)}</code></p>")
                appendCodeBlock(error.stackTrace, "text", "Stack Trace", null)
                if (error.output.isNotEmpty()) {
                    appendCodeBlock(error.output, "text", "Output", null)
                }
            }

            is ValidationError.SolutionLacksEntropy -> {
                appendCodeBlock(error.solutionCode, error.solutionLanguage, "Solution Code", error.solutionPath)
                appendLine("<h3>Entropy Analysis</h3>")
                appendLine("<p>${error.inputCount} inputs to <code class=\"inline\">${escapeHtml(error.executableName)}</code> " +
                    "only generated ${error.distinctResults} distinct results.</p>")
                error.resultSample?.let {
                    appendLine("<p>Sample result: <code class=\"inline\">${escapeHtml(it)}</code></p>")
                }
                if (error.fauxStatic) {
                    appendLine("<div class=\"alert alert-warning\">Note: The solution is being tested as a faux static method, which may cause problems.</div>")
                }
                appendSuggestion(
                    "Possible Fixes",
                    listOf("Add or adjust the @RandomParameters method or @FixedParameters field"),
                )
            }

            is ValidationError.SolutionDeadCode -> {
                appendCodeBlock(error.solutionCode, error.solutionLanguage, "Solution Code", error.solutionPath)
                appendLine("<h3>Dead Code Analysis</h3>")
                appendLine("<p>Found ${error.deadCodeLines} lines of untested code (maximum allowed: ${error.maximumAllowed}).</p>")
                appendLine("<p>Dead lines: ${error.deadLineNumbers.joinToString(", ")}</p>")
                appendSuggestion(
                    "Possible Fixes",
                    listOf(
                        "Adjust the inputs to test more code paths",
                        "Prune unused code paths",
                        "Increase the amount of allowed dead code",
                    ),
                )
            }

            is ValidationError.NoIncorrect -> {
                appendCodeBlock(error.solutionCode, error.solutionLanguage, "Solution Code", error.solutionPath)
                appendSuggestion(
                    "Possible Fixes",
                    listOf(
                        "Add incorrect examples using the @Incorrect annotation",
                        "Enable suppressed mutations",
                    ),
                )
            }

            is ValidationError.TooFewMutations -> {
                appendCodeBlock(error.solutionCode, error.solutionLanguage, "Solution Code", error.solutionPath)
                appendLine("<h3>Mutation Analysis</h3>")
                appendLine("<p>Generated ${error.foundCount} mutations, but needed ${error.neededCount}.</p>")
                appendSuggestion(
                    "Possible Fixes",
                    listOf(
                        "Reduce the required number of mutations",
                        "Remove mutation suppressions",
                    ),
                )
            }

            is ValidationError.TooMuchOutput -> {
                appendCodeBlock(error.sourceCode, error.sourceLanguage, "Source Code", error.sourcePath)
                appendLine("<h3>Output Analysis</h3>")
                appendLine("<p>Generated ${error.outputSize} bytes of output, but the maximum is ${error.maxSize}.</p>")
                appendSuggestion(
                    "Possible Fixes",
                    listOf("Reduce the number of tests using <code>@Correct(minTestCount = NUM)</code>"),
                )
            }

            is ValidationError.IncorrectFailedLinting -> {
                appendCodeBlock(error.incorrectCode, error.incorrectLanguage, "Incorrect Code", error.incorrectPath)
                appendLine("<h3>Linting Errors</h3>")
                appendLine("<pre class=\"error-message\">${escapeHtml(error.lintingErrors)}</pre>")
            }

            is ValidationError.IncorrectPassed -> {
                appendCodeBlock(error.incorrectCode, error.incorrectLanguage, "Incorrect Code (should have failed)", error.incorrectPath)
                if (error.isMutation) {
                    appendLine("<div class=\"alert alert-warning\">This is a mutation${error.mutationType?.let { " ($it)" } ?: ""}.</div>")
                }
                appendSuggestion(
                    "Possible Fixes",
                    buildList {
                        add("If the code is incorrect, add a failing input using <code>@FixedParameters</code>")
                        if (error.isMutation && error.suppressionComment != null) {
                            add("If the code is actually correct, disable this mutation using <code>// ${error.suppressionComment}</code>")
                        }
                        add("Increase the test count using <code>@Correct(maxTestCount = NUM)</code>")
                    },
                )
            }

            is ValidationError.IncorrectTooManyTests -> {
                appendCodeBlock(error.incorrectCode, error.incorrectLanguage, "Incorrect Code", error.incorrectPath)
                appendLine("<h3>Test Count Analysis</h3>")
                appendLine("<p>Required ${error.testsRequired} tests to fail, but the limit is ${error.testsLimit}.</p>")
                error.failingInput?.let {
                    appendLine("<p>Found failing input: <code class=\"inline\">${escapeHtml(it)}</code></p>")
                }
                if (error.isMutation) {
                    appendLine("<div class=\"alert alert-warning\">This is a mutation${error.mutationType?.let { " ($it)" } ?: ""}.</div>")
                }
                appendSuggestion(
                    "Possible Fixes",
                    buildList {
                        add("If the code is incorrect, add an input to <code>@FixedParameters</code> to handle this case")
                        if (error.isMutation && error.suppressionComment != null) {
                            add("If the code is correct, disable this mutation using <code>// ${error.suppressionComment}</code>")
                        }
                        add("Increase the test count using <code>@Correct(maxTestCount = NUM)</code>")
                    },
                )
            }

            is ValidationError.IncorrectWrongReason -> {
                appendCodeBlock(error.incorrectCode, error.incorrectLanguage, "Incorrect Code", error.incorrectPath)
                appendLine("<h3>Failure Reason Mismatch</h3>")
                appendLine("<p>Expected: <code class=\"inline\">${escapeHtml(error.expectedReason)}</code></p>")
                appendLine("<p>Actual: <code class=\"inline\">${escapeHtml(error.actualExplanation)}</code></p>")
                appendSuggestion(
                    "Possible Fixes",
                    listOf("Check the arguments to <code>@Incorrect(reason = REASON)</code>"),
                )
            }

            is ValidationError.IncorrectTestingThrew -> {
                appendCodeBlock(error.incorrectCode, error.incorrectLanguage, "Incorrect Code", error.incorrectPath)
                appendLine("<h3>Exception Details</h3>")
                appendLine("<p>Threw: <code class=\"inline\">${escapeHtml(error.thrownException)}</code></p>")
                appendCodeBlock(error.stackTrace, "text", "Stack Trace", null)
                if (error.output.isNotEmpty()) {
                    appendCodeBlock(error.output, "text", "Output", null)
                }
            }

            is ValidationError.UnexpectedError -> {
                appendLine("<h3>Exception Details</h3>")
                appendLine("<p>Type: <code class=\"inline\">${escapeHtml(error.exceptionType)}</code></p>")
                appendCodeBlock(error.stackTrace, "text", "Stack Trace", null)
            }
        }
    }

    /**
     * Append a code block to the StringBuilder.
     */
    private fun StringBuilder.appendCodeBlock(code: String, language: String, title: String, path: String?) {
        val langClass = when (language.lowercase()) {
            "java" -> "java"
            "kotlin" -> "kotlin"
            else -> "text"
        }
        appendLine("<div class=\"code-block\">")
        appendLine("  <div class=\"code-block-header\">")
        appendLine("    <span>$title</span>")
        path?.let { appendLine("    <span>${escapeHtml(it)}</span>") }
        appendLine("  </div>")
        appendLine("  <pre><code class=\"language-$langClass\">${escapeHtml(code)}</code></pre>")
        appendLine("</div>")
    }

    /**
     * Append a suggestion box to the StringBuilder.
     */
    private fun StringBuilder.appendSuggestion(title: String, suggestions: List<String>) {
        appendLine("<div class=\"suggestion\">")
        appendLine("  <div class=\"suggestion-title\">$title</div>")
        appendLine("  <ul>")
        suggestions.forEach { appendLine("    <li>$it</li>") }
        appendLine("  </ul>")
        appendLine("</div>")
    }

    /**
     * Escape HTML characters.
     */
    private fun escapeHtml(text: String): String = StringEscapeUtils.escapeHtml4(text)

    /**
     * Format a summary report that links to individual question reports.
     */
    fun formatSummaryHtml(
        results: List<ValidationResult>,
    ): String {
        val successes = results.filterIsInstance<ValidationResult.Success>()
        val failures = results.filterIsInstance<ValidationResult.Failure>()
        val total = results.size
        val successCount = successes.size
        val failCount = failures.size
        val successPercent = if (total > 0) (successCount * 100 / total) else 100

        val body = buildString {
            appendLine("<header>")
            appendLine("  <h1>Validation Summary</h1>")
            appendLine("  <p class=\"timestamp\">${java.time.LocalDateTime.now()}</p>")
            appendLine("</header>")

            appendLine("<div class=\"summary\">")
            appendLine("  <div class=\"stat\">")
            appendLine("    <div class=\"stat-value\">$total</div>")
            appendLine("    <div class=\"stat-label\">Total</div>")
            appendLine("  </div>")
            appendLine("  <div class=\"stat success\">")
            appendLine("    <div class=\"stat-value\">$successCount</div>")
            appendLine("    <div class=\"stat-label\">Passed</div>")
            appendLine("  </div>")
            appendLine("  <div class=\"stat failure\">")
            appendLine("    <div class=\"stat-value\">$failCount</div>")
            appendLine("    <div class=\"stat-label\">Failed</div>")
            appendLine("  </div>")
            appendLine("  <div class=\"stat\">")
            appendLine("    <div class=\"stat-value\">$successPercent%</div>")
            appendLine("    <div class=\"stat-label\">Pass Rate</div>")
            appendLine("  </div>")
            appendLine("</div>")

            // Progress bar
            appendLine("<div style=\"height: 8px; background: #ff6b6b; border-radius: 4px; overflow: hidden; margin-bottom: 2rem;\">")
            appendLine("  <div style=\"height: 100%; background: #4ade80; width: $successPercent%;\"></div>")
            appendLine("</div>")

            // Failures section
            if (failures.isNotEmpty()) {
                appendLine("<h2 style=\"color: #ff6b6b;\">Failures (${failures.size})</h2>")
                appendLine("<div style=\"display: flex; flex-direction: column; gap: 0.75rem;\">")
                failures.forEach { failure ->
                    val reportPath = getReportPath(failure.questionPath)
                    val phaseBadge = if (failure.phase == ValidationPhase.VALIDATE) "validate" else "calibrate"
                    appendLine("<div class=\"error-details\">")
                    appendLine("  <div class=\"error-header\">")
                    appendLine("    <span class=\"badge badge-failure\">$phaseBadge</span>")
                    appendLine("    <a href=\"$reportPath\" style=\"color: #f87171; text-decoration: none; font-weight: 500;\">${escapeHtml(failure.questionDisplayName())}</a>")
                    appendLine("  </div>")
                    appendLine("  <div class=\"error-body\" style=\"padding: 1rem;\">")
                    appendLine("    <p style=\"color: #94a3b8;\">${escapeHtml(failure.error.errorType)}: ${escapeHtml(failure.error.message.take(200))}</p>")
                    appendLine("  </div>")
                    appendLine("</div>")
                }
                appendLine("</div>")
            }

            // Successes section
            if (successes.isNotEmpty()) {
                appendLine("<h2 style=\"color: #4ade80;\">Passed (${successes.size})</h2>")
                appendLine("<div style=\"display: flex; flex-direction: column; gap: 0.5rem;\">")
                successes.forEach { success ->
                    val reportPath = getReportPath(success.questionPath)
                    val phaseBadge = if (success.phase == ValidationPhase.VALIDATE) "validate" else "calibrate"
                    appendLine("<div style=\"background: #16213e; padding: 0.75rem 1rem; border-radius: 6px; display: flex; align-items: center; gap: 0.75rem; border-left: 4px solid #4ade80;\">")
                    appendLine("  <span class=\"badge badge-success\">$phaseBadge</span>")
                    appendLine("  <a href=\"$reportPath\" style=\"color: #4ade80; text-decoration: none;\">${escapeHtml(success.questionDisplayName())}</a>")
                    appendLine("  <span style=\"color: #64748b; margin-left: auto;\">${success.summary.testCount} tests, ${success.durationMs}ms</span>")
                    appendLine("</div>")
                }
                appendLine("</div>")
            }
        }

        return wrapDocument("Validation Summary - $failCount failures", body)
    }

    /**
     * Get the relative path to a question's report from the summary report.
     */
    private fun getReportPath(questionPath: String): String {
        // questionPath is like .../build/questioner/questions/{hash}.parsed.json
        // Report is at .../build/questioner/questions/{hash}/report.html
        // Summary is at .../build/questioner/validation-report.html
        // So relative path is questions/{hash}/report.html
        val questionFile = java.io.File(questionPath)
        val hash = questionFile.nameWithoutExtension.removeSuffix(".parsed")
        return "questions/$hash/report.html"
    }
}
