package edu.illinois.cs.cs124.stumperd.server

enum class Steps(val value: Int) {
    IDENTIFY(1),
    DEDUPLICATE(2),
    CLEAN(3),
    REDEDUPLICATE(4),
    VALIDATE(5),
}
