@file:Suppress("unused")
@file:JvmName("FeatureHelpers")

package edu.illinois.cs.cs125.questioner.lib.features

import edu.illinois.cs.cs125.jeed.core.FeatureName
import edu.illinois.cs.cs125.jeed.core.Features

fun Features.usesLoop() = featureMap[FeatureName.WHILE_LOOPS] > 0
    || featureMap[FeatureName.FOR_LOOPS] > 0
    || featureMap[FeatureName.DO_WHILE_LOOPS] > 0
    || dottedMethodList.contains("forEach")

fun Features.hasFeature(feature: FeatureName) = featureMap[feature] > 0
fun Features.doesNotHaveFeature(feature: FeatureName) = featureMap[feature] == 0