// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.jdbc.clickhouse.runtime

import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier
import io.quarkus.agroal.runtime.AgroalConnectionConfigurer
import io.quarkus.agroal.runtime.JdbcDriver

private const val DB_KIND = "clickhouse"

@JdbcDriver(DB_KIND)
public class ClickhouseAgroalConnectionConfigurer : AgroalConnectionConfigurer {
    override fun disableSslSupport(
        databaseKind: String,
        dataSourceConfiguration: AgroalDataSourceConfigurationSupplier,
        additionalJdbcProperties: Map<String, String>,
    ) {
        dataSourceConfiguration.connectionPoolConfiguration()
            .connectionFactoryConfiguration()
            .jdbcProperty("ssl", "false")
    }

    override fun setExceptionSorter(
        databaseKind: String,
        dataSourceConfiguration: AgroalDataSourceConfigurationSupplier
    ) {
        dataSourceConfiguration.connectionPoolConfiguration()
            .exceptionSorter(ClickhouseExceptionSorter())
    }
}
