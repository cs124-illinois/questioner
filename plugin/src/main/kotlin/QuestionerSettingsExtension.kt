package edu.illinois.cs.cs125.questioner.plugin

open class QuestionerSettingsExtension {
    internal data class ExternalDir(val path: String, val excludes: List<String> = emptyList())

    internal val externalDirs: MutableList<ExternalDir> = mutableListOf()

    fun external(path: String) {
        externalDirs.add(ExternalDir(path))
    }

    fun external(path: String, configure: ExternalDirConfig.() -> Unit) {
        val config = ExternalDirConfig()
        config.configure()
        externalDirs.add(ExternalDir(path, config.excludes.toList()))
    }

    class ExternalDirConfig {
        internal val excludes = mutableListOf<String>()
        fun exclude(vararg patterns: String) {
            excludes.addAll(patterns)
        }
    }
}
