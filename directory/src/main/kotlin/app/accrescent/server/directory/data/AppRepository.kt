// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory.data

import io.quarkus.hibernate.reactive.panache.kotlin.PanacheRepositoryBase
import io.smallrye.mutiny.Uni
import jakarta.enterprise.context.ApplicationScoped

/**
 * Data repository for accessing app information
 */
@ApplicationScoped
class AppRepository : PanacheRepositoryBase<App, String> {
    /**
     * Deletes all apps matching the specified ids
     *
     * @param ids the IDs of the apps to delete
     * @return a [Uni] which can drive the deletion to completion
     */
    fun deleteByIds(ids: List<String>): Uni<Void> {
        return delete("WHERE id IN ?1", ids).replaceWithVoid()
    }
}
