package com.github.cs124_illinois.questioner.testing.badextraprivatevariable.incorrect.kotlin.extraprivatefield

import com.github.cs124_illinois.questioner.testing.badextraprivatevariable.Parent
import edu.illinois.cs.cs125.questioner.lib.Incorrect

@Incorrect("features")
class Child(private val firstName: String?, private val lastName: String?) : Parent(lastName) {
  override fun toString() = "$firstName $lastName"
}