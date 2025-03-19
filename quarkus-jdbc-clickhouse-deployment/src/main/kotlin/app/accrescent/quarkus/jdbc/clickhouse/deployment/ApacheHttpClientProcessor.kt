// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.jdbc.clickhouse.deployment

import io.quarkus.deployment.annotations.BuildProducer
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem

class ApacheHttpClientProcessor {
    @BuildStep
    fun registerRuntimeInitializedClasses(items: BuildProducer<RuntimeInitializedClassBuildItem>) {
        items.produce(
            RuntimeInitializedClassBuildItem("org.apache.hc.client5.http.impl.auth.NTLMEngineImpl")
        )
    }
}
