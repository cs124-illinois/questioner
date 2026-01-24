package com.github.cs124_illinois.questioner.testing.withkotlinfauxproperty.incorrect.withfauxproperty

import edu.illinois.cs.cs125.questioner.lib.Incorrect

@Incorrect(reason = "design")
class Book {
  private var name: String? = null
  fun getName(): String? = name
  fun setName(setName: String?) {
    name = setName
  }
}