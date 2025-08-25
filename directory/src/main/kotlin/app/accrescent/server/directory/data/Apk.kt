// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory.data

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
 * An APK belonging to an app
 *
 * @property id this APK's ID in object storage
 * @property releaseChannelId the unique ID of this APK's associated release channel
 * @property uncompressedSize the uncompressed size of this APK in bytes
 * @property releaseChannel this APK's associated release channel
 */
@Entity
@Table(name = "apks")
class Apk(
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
    companion object : PanacheCompanionBase<Apk, String> {
        /**
         * Finds a set of APKs by their object storage IDs
         *
         * @param ids the list of APK object IDs to fetch
         * @return the APKs matching the provided IDs
         */
        fun findByIds(ids: List<String>): Uni<List<Apk>> {
            return find("id IN ?1", ids).list()
        }
    }
}
