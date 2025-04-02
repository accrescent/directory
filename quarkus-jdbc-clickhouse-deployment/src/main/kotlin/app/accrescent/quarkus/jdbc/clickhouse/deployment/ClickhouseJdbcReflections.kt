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
            listOf(
                ReflectiveClassBuildItem.builder(DRIVER_NAME)
                    .methods(false)
                    .fields(false)
                    .build(),
                ReflectiveClassBuildItem.builder(DATA_SOURCE_NAME)
                    .methods(false)
                    .fields(false)
                    .build(),
                ReflectiveClassBuildItem.builder("net.jpountz.lz4.LZ4HCJavaSafeCompressor")
                    .fields(true)
                    .build(),
                ReflectiveClassBuildItem.builder("net.jpountz.lz4.LZ4JavaSafeCompressor")
                    .fields(true)
                    .build(),
                ReflectiveClassBuildItem.builder("net.jpountz.lz4.LZ4JavaSafeFastDecompressor")
                    .fields(true)
                    .build(),
                ReflectiveClassBuildItem.builder("net.jpountz.lz4.LZ4JavaSafeSafeDecompressor")
                    .fields(true)
                    .build(),
            )
        )
    }
}
