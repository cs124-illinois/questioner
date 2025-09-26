package edu.illinois.cs.cs125.questioner.plugin

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe
import org.gradle.testfixtures.ProjectBuilder

class TestGetVersion :
    StringSpec({
        "it should get the version" {
            val project = ProjectBuilder.builder().build()
            getLatestQuestionerVersion(project) shouldNotBe ""
            isLatestVersion(project)
        }
    })
