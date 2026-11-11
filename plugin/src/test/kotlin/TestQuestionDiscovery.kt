package edu.illinois.cs.cs125.questioner.plugin

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

class TestQuestionDiscovery :
    StringSpec({
        fun File.question(name: String, contents: String) = resolve(name).also { it.writeText(contents.trimIndent()) }

        "it should discover a question whose name contains parentheses" {
            val directory = tempdir()
            val file = directory.question(
                "Question.java",
                """
                @Correct(name = "Array Max (1D)", version = "2026.11.0", author = "challen@illinois.edu")
                public class Question {}
                """,
            )
            val discovered = file.extractCorrectInfo(directory)
            discovered shouldNotBe null
            discovered!!.author shouldBe "challen@illinois.edu"
            discovered.slug shouldBe "array-max-1d"
        }
        "it should discover a question with a parenthesized name across lines" {
            val directory = tempdir()
            val file = directory.question(
                "Question.kt",
                """
                @Correct(
                  name = "Quicksort Partition (First Value)",
                  version = "2026.11.0",
                  author = "challen@illinois.edu",
                  path = "quicksort-partition-first",
                )
                fun partition() {}
                """,
            )
            file.extractCorrectInfo(directory)!!.slug shouldBe "quicksort-partition-first"
        }
        "it should keep an escaped quote inside a name" {
            val directory = tempdir()
            val file = directory.question(
                "Question.java",
                """
                @Correct(name = "Say \"Hi (Twice)\"", version = "2026.11.0", author = "challen@illinois.edu")
                public class Question {}
                """,
            )
            val discovered = file.extractCorrectInfo(directory)
            discovered shouldNotBe null
            discovered!!.author shouldBe "challen@illinois.edu"
            discovered.slug shouldBe "say-hi-twice"
        }
        "it should report @Correct files it cannot read" {
            val directory = tempdir()
            directory.question(
                "Good.java",
                """
                @Correct(name = "Good (1D)", version = "2026.11.0", author = "challen@illinois.edu")
                public class Good {}
                """,
            )
            val missingAuthor = directory.question(
                "Missing.java",
                """
                @Correct(name = "Missing Author", version = "2026.11.0")
                public class Missing {}
                """,
            )
            directory.question(
                "Other.java",
                """
                import edu.illinois.cs.cs125.questioner.lib.Correct;
                public class Other {}
                """,
            )
            val skipped = mutableListOf<File>()
            discoverQuestions(directory, onSkipped = { skipped.add(it) }) shouldHaveSize 1
            skipped shouldContainExactly listOf(missingAuthor)
        }
    })
