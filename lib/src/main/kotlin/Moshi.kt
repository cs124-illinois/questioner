package edu.illinois.cs.cs125.questioner.lib

import com.squareup.moshi.Moshi
import edu.illinois.cs.cs125.jeed.core.moshi.Adapters as JeedAdapters
import edu.illinois.cs.cs125.questioner.lib.moshi.Adapters as QuestionerAdapters

val moshi: Moshi = Moshi.Builder().apply {
    QuestionerAdapters.forEach { adapter -> add(adapter) }
    JeedAdapters.forEach { adapter -> add(adapter) }
}.build()
