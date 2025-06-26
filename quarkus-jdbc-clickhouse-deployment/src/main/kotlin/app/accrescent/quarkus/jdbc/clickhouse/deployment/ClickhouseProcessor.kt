// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.jdbc.clickhouse.deployment

import io.quarkus.deployment.annotations.BuildProducer
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem

class ClickhouseProcessor {
    @BuildStep
    fun registerRuntimeInitializedClasses(items: BuildProducer<RuntimeInitializedClassBuildItem>) {
        items.produce(
            listOf(
                RuntimeInitializedClassBuildItem("com.clickhouse.client.api.data_formats.internal.SerializerUtils"),
                RuntimeInitializedClassBuildItem("com.clickhouse.data.value.ClickHouseIpv4Value"),
                RuntimeInitializedClassBuildItem("com.clickhouse.data.value.ClickHouseIpv6Value"),
            )
        )
    }
}
