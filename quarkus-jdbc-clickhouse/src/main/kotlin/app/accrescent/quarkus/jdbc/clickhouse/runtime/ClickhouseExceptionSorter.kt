// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.quarkus.jdbc.clickhouse.runtime

import io.agroal.api.configuration.AgroalConnectionPoolConfiguration
import java.sql.SQLException

internal class ClickhouseExceptionSorter : AgroalConnectionPoolConfiguration.ExceptionSorter {
    override fun isFatal(e: SQLException): Boolean {
        return e.sqlState?.startsWith("08") == true
    }
}
