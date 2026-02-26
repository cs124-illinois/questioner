package edu.illinois.cs.cs125.questioner.plugin

open class QuestionerSettingsExtension {
    internal val externalDirs: MutableList<String> = mutableListOf()

    fun external(path: String) {
        externalDirs.add(path)
    }
}
