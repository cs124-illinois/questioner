@file:Suppress("MatchingDeclarationName", "SpreadOperator", "unused")

package edu.illinois.cs.cs125.questioner.lib

import java.lang.reflect.Method
import java.lang.reflect.Modifier

@Suppress("TooManyFunctions")
data class AnalyzeClass(val klass: Class<*>) {
    val name: String = klass.name
    private val modifiers = klass.modifiers

    fun isPublic(): AnalyzeClass {
        check(Modifier.isPublic(modifiers)) { "Class $name should be public" }
        return this
    }

    fun isNotPublic(): AnalyzeClass {
        check(!Modifier.isPublic(modifiers)) { "Class $name should not be public" }
        return this
    }

    fun isFinal(): AnalyzeClass {
        check(Modifier.isFinal(modifiers)) { "Class $name should be final" }
        return this
    }

    fun isNotFinal(): AnalyzeClass {
        check(!Modifier.isFinal(modifiers)) { "Class $name should not be final" }
        return this
    }

    fun isAbstract(): AnalyzeClass {
        check(Modifier.isAbstract(modifiers)) { "Class $name should be abstract" }
        return this
    }

    fun isNotAbstract(): AnalyzeClass {
        check(!Modifier.isAbstract(modifiers)) { "Class $name should not be abstract" }
        return this
    }

    fun hasAtMostPublicFields(count: Int): AnalyzeClass {
        klass.declaredFields.filter { Modifier.isPublic(it.modifiers) }.size.also {
            check(it <= count) {
                "Class $name should have at most $count public fields (currently has $it)"
            }
        }
        return this
    }

    fun hasNoPublicFields(): AnalyzeClass {
        klass.declaredFields.filter { Modifier.isPublic(it.modifiers) }.size.also {
            check(it == 0) {
                "Class $name should not declare any public fields (currently has $it)"
            }
        }
        return this
    }

    fun inheritsFrom(parent: Class<*>): AnalyzeClass {
        check(parent.isAssignableFrom(klass)) {
            "Class $name does not inherit from ${klass.name}"
        }
        return this
    }

    fun shouldNotOverride(name: String, vararg parameters: Class<*>?): AnalyzeClass {
        try {
            klass.getDeclaredMethod(name, *parameters)
            error(
                "Class $name should not provide method " +
                    "$name(${parameters.joinToString(separator = ",") { it?.name ?: "null" }})"
            )
        } catch (_: NoSuchMethodException) {
        }
        return this
    }

    @Suppress("MemberVisibilityCanBePrivate")
    val isKotlin = klass.getAnnotation(Metadata::class.java) != null
    val hasInnerClasses = klass.declaredClasses.isNotEmpty()
    val isLambda = klass.isSynthetic && !(klass.isLocalClass || klass.isAnonymousClass) && name.contains("""${"$"}${"$"}Lambda/""")

    @Suppress("ReplaceCallWithBinaryOperator")
    companion object {
        @JvmStatic
        fun checkEquals(same: Array<Any>, different: Array<Any?>) {
            same.forEach { self ->
                check(self.equals(self)) {
                    "Instance of ${self.javaClass.name} does not equal itself"
                }
                @Suppress("EqualsNullCall")
                check(!self.equals(null)) {
                    "Instance of ${self.javaClass.name} should not equal null"
                }
                same.filter { it !== self }.forEach { same ->
                    check(self.equals(same)) {
                        "${self.javaClass.name} equals not correct"
                    }
                }
                different.forEach { different ->
                    check(!self.equals(different)) {
                        if (self.javaClass == different?.javaClass) {
                            "${self.javaClass.name} equals not correct"
                        } else {
                            "${self.javaClass.name} equals does not handle other types properly"
                        }
                    }
                }
            }
            return
        }
    }
}

@Suppress("ArrayInDataClass")
data class AnalyzeMethod(val klass: Class<*>, val name: String, val parameters: Array<Class<*>> = arrayOf()) {
    constructor(klass: Class<*>, name: String, parameter: Class<*>) : this(klass, name, arrayOf(parameter))

    private val klassName: String = klass.name

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    val method: Method = try {
        klass.getDeclaredMethod(name, *parameters)
    } catch (e: Exception) {
        error("$klassName has no method $name")
    }
    private val returnType: Class<*> = method.returnType
    private val modifiers: Int = method.modifiers

    fun isPublic(): AnalyzeMethod {
        check(Modifier.isPublic(modifiers)) { "Method $name (on class $klassName) should be public" }
        return this
    }

    fun isNotPublic(): AnalyzeMethod {
        check(!Modifier.isPublic(modifiers)) { "Method $name (on class $klassName) should not be public" }
        return this
    }

    fun isStatic(): AnalyzeMethod {
        check(Modifier.isStatic(modifiers)) { "Method $name (on class $klassName) should be static" }
        return this
    }

    fun isNotStatic(): AnalyzeMethod {
        check(!Modifier.isStatic(modifiers)) { "Method $name (on class $klassName) should not be static" }
        return this
    }

    fun returns(shouldReturn: Class<*>): AnalyzeMethod {
        check(shouldReturn == returnType) {
            "Method $name (on class $klassName) should return ${shouldReturn.name}"
        }
        return this
    }
}
