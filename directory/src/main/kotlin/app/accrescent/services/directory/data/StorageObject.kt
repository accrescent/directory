// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.data

import io.quarkus.hibernate.reactive.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.reactive.panache.kotlin.PanacheEntityBase
import io.smallrye.mutiny.Uni
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

/**
 * A blob stored in an object storage bucket
 *
 * @property id this object's unique ID
 * @property releaseChannelId the unique ID of this object's associated release channel
 * @property uncompressedSize the uncompressed size of this object in bytes
 * @property releaseChannel this object's associated release channel
 */
@Entity
@Table(name = "objects")
class StorageObject(
    @Column(columnDefinition = "text")
    @Id
    val id: String,

    @Column(name = "release_channel_id", nullable = false)
    val releaseChannelId: UUID,

    @Column(name = "uncompressed_size", nullable = false)
    val uncompressedSize: UInt
) : PanacheEntityBase {
    @ManyToOne
    @JoinColumn(name = "release_channel_id", insertable = false, updatable = false)
    lateinit var releaseChannel: ReleaseChannel

    /**
     * Container for related methods
     */
    companion object : PanacheCompanionBase<StorageObject, String> {
        /**
         * Finds a set of objects by their IDs
         *
         * @param ids the list of objects to fetch
         * @return the objects matching the provided IDs
         */
        fun findByIds(ids: List<String>): Uni<List<StorageObject>> {
            return find("id IN ?1", ids).list()
        }
    }
}
