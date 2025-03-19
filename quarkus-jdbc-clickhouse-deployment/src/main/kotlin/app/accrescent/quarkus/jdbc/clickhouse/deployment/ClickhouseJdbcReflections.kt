// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.jdbc.clickhouse.deployment

import io.quarkus.deployment.annotations.BuildProducer
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem

class ClickhouseJdbcReflections {
    @BuildStep
    fun build(items: BuildProducer<ReflectiveClassBuildItem>) {
        items.produce(
            ReflectiveClassBuildItem.builder(DRIVER_NAME)
                .methods(false)
                .fields(false)
                .build()
        )
        items.produce(
            ReflectiveClassBuildItem.builder(DATA_SOURCE_NAME)
                .methods(false)
                .fields(false)
                .build()
        )
    }
}
