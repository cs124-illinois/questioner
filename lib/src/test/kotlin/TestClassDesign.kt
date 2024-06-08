package edu.illinois.cs.cs125.questioner.lib

import com.examples.ClassDesignTester
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TestClassDesign : StringSpec({
    "it should detecte lambdas" {
        val javaLambda = ClassDesignTester.generateAddOne()
        AnalyzeClass(javaLambda::class.java).also { analyzed ->
            analyzed.isLambda shouldBe true
        }
        val kotlinLambda = { it: Int -> it % 2 == 0 }
        AnalyzeClass(kotlinLambda::class.java).also { analyzed ->
            analyzed.isLambda shouldBe true
        }
    }
})