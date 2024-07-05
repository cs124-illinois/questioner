package edu.illinois.cs.cs125.questioner.lib

@Suppress("unused")
suspend fun warm(question: Question? = null, timeoutExpansion: Double = 10.0) {
    ResourceMonitoring.hashCode()

    if (question == null) {
        return
    }

    check(question.validated) { "Question not validated" }
    question.solutionByLanguage.forEach { (language, flatFile) ->
        question.test(flatFile.contents, language, question.testingSettings!!.copy(timeoutMultiplier = timeoutExpansion))
            .also { testResults ->
                check(testResults.succeeded) { "Warming ${question.usefulPath} with $language solution did not succeed" }
            }
    }
}