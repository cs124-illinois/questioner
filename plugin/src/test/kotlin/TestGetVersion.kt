package edu.illinois.cs.cs125.questioner.plugin

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe

class TestGetVersion :
    StringSpec({
        "it should get the version" {
            getLatestQuestionerVersion() shouldNotBe ""
            isLatestVersion()
        }
    })
