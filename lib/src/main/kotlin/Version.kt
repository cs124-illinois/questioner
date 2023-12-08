package edu.illinois.cs.cs125.questioner.lib

import java.util.Properties

val VERSION: String = Properties().also {
    it.load((object {}).javaClass.getResourceAsStream("/edu.illinois.cs.cs124.questioner.lib.version"))
}.getProperty("version")
