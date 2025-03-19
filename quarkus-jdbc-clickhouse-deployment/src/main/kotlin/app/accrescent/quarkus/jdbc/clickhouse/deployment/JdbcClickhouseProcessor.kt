// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.jdbc.clickhouse.deployment

import app.accrescent.quarkus.jdbc.clickhouse.runtime.ClickhouseAgroalConnectionConfigurer
import io.quarkus.agroal.spi.JdbcDriverBuildItem
import io.quarkus.arc.deployment.AdditionalBeanBuildItem
import io.quarkus.arc.processor.BuiltinScope
import io.quarkus.datasource.deployment.spi.DefaultDataSourceDbKindBuildItem
import io.quarkus.datasource.deployment.spi.DevServicesDatasourceConfigurationHandlerBuildItem
import io.quarkus.deployment.Capabilities
import io.quarkus.deployment.Capability
import io.quarkus.deployment.annotations.BuildProducer
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.FeatureBuildItem

private const val DB_KIND = "clickhouse"
private const val FEATURE_NAME = "jdbc-clickhouse"

class JdbcClickhouseProcessor {
    @BuildStep
    fun feature(): FeatureBuildItem {
        return FeatureBuildItem(FEATURE_NAME)
    }

    @BuildStep
    fun registerDriver(jdbcDriver: BuildProducer<JdbcDriverBuildItem>) {
        jdbcDriver.produce(JdbcDriverBuildItem(DB_KIND, DRIVER_NAME, DATA_SOURCE_NAME))
    }

    @BuildStep
    fun devDbHandler(): DevServicesDatasourceConfigurationHandlerBuildItem {
        return DevServicesDatasourceConfigurationHandlerBuildItem.jdbc(DB_KIND)
    }

    @BuildStep
    fun configureAgroalConnection(
        additionalBeans: BuildProducer<AdditionalBeanBuildItem>,
        capabilities: Capabilities,
    ) {
        if (capabilities.isPresent(Capability.AGROAL)) {
            additionalBeans.produce(
                AdditionalBeanBuildItem.builder()
                    .addBeanClass(ClickhouseAgroalConnectionConfigurer::class.java)
                    .setDefaultScope(BuiltinScope.APPLICATION.getName())
                    .setUnremovable()
                    .build()
            )
        }
    }

    @BuildStep
    fun registerDefaultDbType(dbKind: BuildProducer<DefaultDataSourceDbKindBuildItem>) {
        dbKind.produce(DefaultDataSourceDbKindBuildItem(DB_KIND))
    }
}
