@file:Suppress("unused")

package edu.illinois.cs.cs125.questioner.lib.moshi

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import edu.illinois.cs.cs125.jeed.core.moshi.InstantAdapter
import edu.illinois.cs.cs125.questioner.lib.ResourceMonitoringResults
import java.time.Instant
import edu.illinois.cs.cs125.jeed.core.moshi.Adapters as JeedAdapters

val Adapters = setOf(
    InstantAdapter(),
    MethodInfoAdapter()
)

class InstantAdapter {
    @FromJson
    fun instantFromJson(timestamp: String): Instant {
        return Instant.parse(timestamp)
    }

    @ToJson
    fun instantToJson(instant: Instant): String {
        return instant.toString()
    }
}

@JsonClass(generateAdapter = true)
data class MethodInfo(val className: String, val methodName: String, val descriptor: String)
class MethodInfoAdapter {
    @FromJson
    fun methodInfoFromJson(info: MethodInfo) =
        ResourceMonitoringResults.MethodInfo(info.className, info.methodName, info.descriptor)

    @ToJson
    fun methodInfoToJson(info: ResourceMonitoringResults.MethodInfo) =
        MethodInfo(info.className, info.methodName, info.descriptor)
}

val moshi: Moshi = Moshi.Builder().apply {
    Adapters.forEach { adapter -> add(adapter) }
    JeedAdapters.forEach { adapter -> add(adapter) }
}.build()