package edu.illinois.cs.cs125.questioner.lib

import edu.illinois.cs.cs125.jeed.core.*
import edu.illinois.cs.cs125.jeed.core.moshi.CompiledSourceResult
import edu.illinois.cs.cs125.jenisol.core.isPackagePrivate
import edu.illinois.cs.cs125.jenisol.core.isPrivate
import edu.illinois.cs.cs125.jenisol.core.isStatic
import org.objectweb.asm.*
import java.lang.reflect.InvocationTargetException

suspend fun Question.testTests(
    contents: String,
    language: Question.Language
): TestTestResults {
    compileAllValidationMutations()

    val testKlass = "Test$klass"
    val results = TestTestResults(language)

    val compilationClassLoader = when (language) {
        Question.Language.java -> InvertingClassLoader(
            setOf(testKlass), compiledSolutionForTesting.classloader
        )

        Question.Language.kotlin -> InvertingClassLoader(
            setOf(testKlass, "${testKlass}Kt"), compiledSolutionForTesting.classloader
        )
    }
    val compiledSubmission = try {
        when (language) {
            Question.Language.java -> compileTestSuites(contents, compilationClassLoader, results)
            Question.Language.kotlin -> kompileTestSuites(contents, compilationClassLoader, results)
        }
    } catch (e: TemplatingFailed) {
        return results
    } catch (e: CompilationFailed) {
        return results
    } catch (e: CheckstyleFailed) {
        return results
    } catch (e: KtLintFailed) {
        return results
    }

    // checkCompiledSubmission
    val klassName = checkCompiledTestSuite(compiledSubmission, results) ?: return results

    val testingLoaders =
        setOf(compiledSolutionForTesting) + validationMutations!!.map { it.compiled(this) }
            .toSet()
    val executionArguments = Sandbox.ExecutionArguments(
        timeout = testingSettings!!.timeout.toLong(),
        maxOutputLines = testingSettings!!.outputLimit,
        permissions = Question.SAFE_PERMISSIONS,
        returnTimeout = Question.DEFAULT_RETURN_TIMEOUT
    )

    val testingClass = compiledSubmission.classLoader.loadClass(klassName)
    val staticTestingMethod = testingClass.getTestingMethod()!!.isStatic()
    if (!staticTestingMethod) {
        check(testingClass.declaredConstructors.find { it.parameters.isEmpty() } != null) {
            "Non-static testing method needs an empty constructor"
        }
    }

    var correct = 0
    var incorrect = 0

    for (testingLoader in testingLoaders.shuffled()) {
        val isSolution = testingLoader == compiledSolutionForTesting

        val testingSuiteLoader = CopyableClassLoader.copy(compiledSubmission.classLoader, testingLoader.classloader)
        val taskResults = Sandbox.execute(
            testingSuiteLoader,
            executionArguments
        ) { (classLoader, _) ->
            return@execute try {
                classLoader.loadClass(klassName).getTestingMethod()!!.also { method ->
                    method.isAccessible = true
                    if (staticTestingMethod) {
                        method.invoke(null)
                    } else {
                        method.invoke(
                            classLoader.loadClass(klassName).declaredConstructors.find { it.parameters.isEmpty() }!!
                                .newInstance()
                        )
                    }
                }
            } catch (e: InvocationTargetException) {
                throw e.cause ?: e
            }
        }
        val isCorrect = if (isSolution) {
            taskResults.threw == null
        } else {
            taskResults.threw != null
        }
        if (isCorrect) {
            correct++
        } else {
            incorrect++
        }
    }

    val succeeded = correct == testingLoaders.size && incorrect == 0
    val shortCircuited = correct + incorrect < testingLoaders.size

    results.addTestTestingResults(TestTestResults.TestTestingResults(succeeded, correct, incorrect, shortCircuited))

    return results
}

fun Question.templateTestSuites(
    contents: String,
    language: Question.Language
): Pair<Source, String?> {
    val template = when (type) {
        Question.Type.KLASS -> null
        Question.Type.METHOD -> {
            when (language) {
                Question.Language.java -> {
                    """public class Test${klass} extends $klass {
  {{{ contents }}}
}
"""
                }

                Question.Language.kotlin -> {
                    """class Test${klass} : ${klass}() {
  {{{ contents }}}
}"""
                }
            }
        }

        Question.Type.SNIPPET -> error("Testing not supported for snippets")
    }

    val fileName = "Test${klass}.${language.extension()}"
    return if (template == null) {
        Pair(Source(mapOf(fileName to contents)), null)
    } else {
        Pair(
            Source.fromTemplates(
                mapOf(fileName to contents.trimEnd()),
                mapOf("$fileName.hbs" to template)
            ), template
        )
    }
}

@Suppress("ThrowsCount")
suspend fun Question.compileTestSuites(
    contents: String,
    parentClassLoader: ClassLoader,
    testResults: TestTestResults
): CompiledSource {
    return try {
        val (source, template) = templateTestSuites(contents, Question.Language.java)
        if (template != null) {
            testResults.completedSteps.add(TestTestResults.Step.templateSubmission)
        }
        val compiledSource = source.compile(
            CompilationArguments(
                parentClassLoader = parentClassLoader,
                parentFileManager = compiledSolution.fileManager,
                parameters = true
            )
        ).also {
            testResults.complete.compileSubmission = CompiledSourceResult(it)
            testResults.completedSteps.add(TestTestResults.Step.compileSubmission)
        }
        testResults.addCheckstyleResults(source.checkstyle(CheckstyleArguments(failOnError = false)))
        compiledSource
    } catch (e: TemplatingFailed) {
        testResults.failed.templateSubmission = e
        testResults.failedSteps.add(TestTestResults.Step.templateSubmission)
        throw e
    } catch (e: CheckstyleFailed) {
        testResults.failed.checkstyle = e
        testResults.failedSteps.add(TestTestResults.Step.checkstyle)
        throw e
    } catch (e: CompilationFailed) {
        testResults.failed.compileSubmission = e
        testResults.failedSteps.add(TestTestResults.Step.compileSubmission)
        throw e
    }
}

@Suppress("ThrowsCount")
suspend fun Question.kompileTestSuites(
    contents: String,
    parentClassLoader: ClassLoader,
    testResults: TestTestResults
): CompiledSource {
    return try {
        val (source, template) = templateTestSuites(contents, Question.Language.kotlin)
        if (template != null) {
            testResults.completedSteps.add(TestTestResults.Step.templateSubmission)
        }
        val compiledSource = source.kompile(
            KompilationArguments(
                parentClassLoader = parentClassLoader,
                parentFileManager = compiledSolution.fileManager,
                parameters = true
            )
        ).also {
            testResults.complete.compileSubmission = CompiledSourceResult(it)
            testResults.completedSteps.add(TestTestResults.Step.compileSubmission)
        }
        testResults.addKtlintResults(source.ktLint(KtLintArguments(failOnError = false)))
        compiledSource
    } catch (e: TemplatingFailed) {
        testResults.failed.templateSubmission = e
        testResults.failedSteps.add(TestTestResults.Step.templateSubmission)
        throw e
    } catch (e: KtLintFailed) {
        testResults.failed.ktlint = e
        testResults.failedSteps.add(TestTestResults.Step.ktlint)
        throw e
    } catch (e: CompilationFailed) {
        testResults.failed.compileSubmission = e
        testResults.failedSteps.add(TestTestResults.Step.compileSubmission)
        throw e
    }
}

private fun Class<*>.getTestingMethod() = declaredMethods.find { testingMethod ->
    testingMethod.name == "test" && testingMethod.parameters.isEmpty() && !testingMethod.isPrivate()
}

fun Question.checkCompiledTestSuite(
    compiledTestSuite: CompiledSource,
    testResults: TestTestResults
): String? = compiledTestSuite.classLoader.definedClasses.topLevelClasses().let {
    val testKlass = "Test$klass"

    when {
        it.isEmpty() -> {
            testResults.failed.checkCompiledSubmission = "Test suite defined no classes"
            testResults.failedSteps.add(TestTestResults.Step.checkCompiledSubmission)
            return null
        }

        it.size > 1 -> {
            testResults.failed.checkCompiledSubmission = "Test suite defined multiple classes"
            testResults.failedSteps.add(TestTestResults.Step.checkCompiledSubmission)
            return null
        }
    }
    var klass = it.first()
    if (compiledTestSuite.source.type == Source.FileType.KOTLIN &&
        (solution.skipReceiver || solution.fauxStatic) &&
        klass == "${testKlass}Kt"
    ) {
        klass = "${testKlass}Kt"
    } else {
        if (klass != testKlass) {
            testResults.failed.checkCompiledSubmission =
                "Test suite defines incorrect class: ${it.first()} != $testKlass"
            testResults.failedSteps.add(TestTestResults.Step.checkCompiledSubmission)
            return null
        }
    }
    compiledTestSuite.classLoader.loadClass(klass).also { testingKlass ->
        testingKlass.getTestingMethod() ?: run {
            testResults.failed.checkCompiledSubmission =
                "Test suite does not define a non-private static testing method named test accepting no arguments"
            testResults.failedSteps.add(TestTestResults.Step.checkCompiledSubmission)
            return null
        }
    }
    return klass
}

class CopyableClassLoader(override val bytecodeForClasses: Map<String, ByteArray>, parent: ClassLoader) :
    ClassLoader(parent), Sandbox.SandboxableClassLoader {
    override val classLoader: ClassLoader = this

    override fun findClass(name: String): Class<*> {
        return if (name in bytecodeForClasses) {
            return defineClass(name, bytecodeForClasses[name]!!, 0, bytecodeForClasses[name]!!.size)
        } else {
            super.findClass(name)
        }
    }

    companion object {
        fun copy(classLoader: JeedClassLoader, parent: ClassLoader) =
            CopyableClassLoader(classLoader.bytecodeForClasses, parent)
    }
}

fun Question.fixTestingMethods(classLoader: JeedClassLoader): ClassLoader {
    val methodsToOpen = classLoader.loadClass(klass).declaredMethods.filter { it.isPackagePrivate() }.map { it.name }
    val classReader = ClassReader(classLoader.bytecodeForClasses[klass])
    val classWriter = ClassWriter(classReader, 0)
    val openingVisitor = object : ClassVisitor(Opcodes.ASM8, classWriter) {
        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String?,
            signature: String?,
            exceptions: Array<out String>?
        ): MethodVisitor {
            return if (name in methodsToOpen) {
                super.visitMethod(access + Opcodes.ACC_PUBLIC, name, descriptor, signature, exceptions)
            } else {
                super.visitMethod(access, name, descriptor, signature, exceptions)
            }
        }
    }
    classReader.accept(openingVisitor, 0)
    return CopyableClassLoader(mapOf(klass to classWriter.toByteArray()), classLoader.parent)
}