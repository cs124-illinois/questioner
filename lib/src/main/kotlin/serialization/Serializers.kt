@file:Suppress("unused")

package edu.illinois.cs.cs125.questioner.lib.serialization

import edu.illinois.cs.cs125.jeed.core.serializers.JeedSerializersModule
import kotlinx.serialization.json.Json

val json = Json {
    serializersModule = JeedSerializersModule
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
