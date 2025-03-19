// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.devservices.clickhouse.deployment

import io.quarkus.datasource.common.runtime.DataSourceUtil
import io.quarkus.datasource.deployment.spi.DatabaseDefaultSetupConfig
import io.quarkus.datasource.deployment.spi.DevServicesDatasourceContainerConfig
import io.quarkus.datasource.deployment.spi.DevServicesDatasourceProvider
import io.quarkus.datasource.deployment.spi.DevServicesDatasourceProviderBuildItem
import io.quarkus.deployment.annotations.BuildStep
import io.quarkus.deployment.builditem.DevServicesSharedNetworkBuildItem
import io.quarkus.deployment.dev.devservices.DevServicesConfig
import io.quarkus.devservices.common.ConfigureUtil
import io.quarkus.devservices.common.ContainerShutdownCloseable
import io.quarkus.devservices.common.JBossLoggingConsumer
import io.quarkus.devservices.common.Labels
import io.quarkus.devservices.common.Volumes
import io.quarkus.runtime.LaunchMode
import org.jboss.logging.Logger
import org.testcontainers.clickhouse.ClickHouseContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.Optional
import java.util.OptionalInt

private const val DB_KIND = "clickhouse"
private const val CLICKHOUSE_IMAGE_NAME = "clickhouse/clickhouse-server"
private const val CLICKHOUSE_HTTP_PORT = 8123

class ClickhouseDevServicesProcessor {
    private companion object {
        private val LOG = Logger.getLogger(ClickhouseDevServicesProcessor::class.java)
    }

    @BuildStep
    fun setupClickhouse(
        devServicesSharedNetworkBuildItem: List<DevServicesSharedNetworkBuildItem>,
        devServicesConfig: DevServicesConfig,
    ): DevServicesDatasourceProviderBuildItem {
        return DevServicesDatasourceProviderBuildItem(DB_KIND, object : DevServicesDatasourceProvider {
            override fun startDatabase(
                username: Optional<String?>,
                password: Optional<String?>,
                datasourceName: String,
                containerConfig: DevServicesDatasourceContainerConfig,
                launchMode: LaunchMode,
                startupTimeout: Optional<Duration?>,
            ): DevServicesDatasourceProvider.RunningDevServicesDatasource {
                val useSharedNetwork = DevServicesSharedNetworkBuildItem.isSharedNetworkRequired(
                    devServicesConfig,
                    devServicesSharedNetworkBuildItem,
                )

                val container = QuarkusClickhouseContainer(
                    containerConfig.imageName,
                    containerConfig.fixedExposedPort,
                    useSharedNetwork,
                )
                startupTimeout.ifPresent(container::withStartupTimeout)

                val effectiveUsername = containerConfig.username.orElse(
                    username.orElse(DatabaseDefaultSetupConfig.DEFAULT_DATABASE_USERNAME),
                )
                val effectivePassword = containerConfig.password.orElse(
                    password.orElse(DatabaseDefaultSetupConfig.DEFAULT_DATABASE_PASSWORD),
                )
                val effectiveDbName = containerConfig.dbName.orElse(
                    if (DataSourceUtil.isDefault(datasourceName)) {
                        DatabaseDefaultSetupConfig.DEFAULT_DATABASE_NAME
                    } else {
                        datasourceName
                    }
                )

                container.withUsername(effectiveUsername)
                    .withPassword(effectivePassword)
                    .withDatabaseName(effectiveDbName)
                    .withReuse(containerConfig.isReuse)
                Labels.addDataSourceLabel(container, datasourceName)
                Volumes.addVolumes(container, containerConfig.volumes)

                container.withEnv(containerConfig.containerEnv)

                containerConfig.additionalJdbcUrlProperties.forEach(container::withUrlParam)
                containerConfig.command.ifPresent(container::setCommand)
                containerConfig.initScriptPath.ifPresent(container::withInitScripts)
                if (containerConfig.isShowLogs) {
                    container.withLogConsumer(JBossLoggingConsumer(LOG))
                }

                container.start()

                LOG.info("Dev Services for ClickHouse started.")

                return DevServicesDatasourceProvider.RunningDevServicesDatasource(
                    container.getContainerId(),
                    container.getEffectiveJdbcUrl(),
                    container.getReactiveUrl(),
                    container.username,
                    container.password,
                    ContainerShutdownCloseable(container, "ClickHouse")
                )
            }
        })
    }
}

private class QuarkusClickhouseContainer(
    imageName: Optional<String>,
    private val fixedExposedPort: OptionalInt,
    private val useSharedNetwork: Boolean,
    private var hostName: String? = null,
) : ClickHouseContainer(
    DockerImageName.parse(
        imageName.orElseGet { ConfigureUtil.getDefaultImageNameFor(DB_KIND) })
        .asCompatibleSubstituteFor(CLICKHOUSE_IMAGE_NAME),
) {
    override fun configure() {
        super.configure()

        if (useSharedNetwork) {
            hostName = ConfigureUtil.configureSharedNetwork(this, DB_KIND)
            return
        }

        if (fixedExposedPort.isPresent) {
            addFixedExposedPort(fixedExposedPort.asInt, CLICKHOUSE_HTTP_PORT)
        } else {
            addExposedPort(CLICKHOUSE_HTTP_PORT)
        }
    }

    fun getEffectiveJdbcUrl(): String {
        return if (useSharedNetwork) {
            val additionalUrlParams = constructUrlParameters("?", "&")
            "jdbc:clickhouse://$hostName:$CLICKHOUSE_HTTP_PORT/$databaseName$additionalUrlParams"
        } else {
            super.jdbcUrl
        }
    }

    fun getReactiveUrl(): String {
        return getEffectiveJdbcUrl().replaceFirst("jdbc:", "vertx-reactive:")
    }
}
