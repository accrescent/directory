// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory.data

import io.quarkus.hibernate.reactive.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.reactive.panache.kotlin.PanacheEntity
import io.smallrye.mutiny.Uni
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

/**
 * An APK belonging to an app
 *
 * @property apkSetPath the path of this APK in its corresponding APK set archive
 * @property objectId this APK's ID in object storage
 * @property releaseChannelId the unique ID of this APK's associated release channel
 * @property uncompressedSize the uncompressed size of this APK in bytes
 * @property releaseChannel this APK's associated release channel
 */
@Entity
@Table(
    name = "apks",
    uniqueConstraints = [UniqueConstraint(columnNames = ["apk_set_path", "release_channel_id"])],
)
class Apk(
    @Column(columnDefinition = "text", name = "apk_set_path", nullable = false)
    val apkSetPath: String,

    @Column(columnDefinition = "text", name = "object_id", unique = true, nullable = false)
    val objectId: String,

    @Column(name = "release_channel_id", nullable = false)
    val releaseChannelId: UUID,

    @Column(name = "uncompressed_size", nullable = false)
    val uncompressedSize: UInt
) : PanacheEntity() {
    @ManyToOne
    @JoinColumn(name = "release_channel_id", insertable = false, updatable = false)
    lateinit var releaseChannel: ReleaseChannel

    /**
     * Container for related methods
     */
    companion object : PanacheCompanionBase<Apk, String> {
        /**
         * Finds a set of APKs by their qualified paths
         *
         * @param appId the app to find APKs for
         * @param releaseChannelName the canonical name of the release channel to find APKs for
         * @param paths the paths of the APKs in the APK set associated with [appId]
         * @return the APKs matching the provided paths
         */
        fun findByQualifiedPaths(
            appId: String,
            releaseChannelName: String,
            paths: List<String>,
        ): Uni<List<Apk>> {
            return find(
                "JOIN ReleaseChannel release_channels " +
                        "ON release_channels.appId = ?1 " +
                        "AND release_channels.name = ?2 " +
                        "WHERE releaseChannelId = release_channels.id " +
                        "AND apkSetPath IN ?3",
                appId,
                releaseChannelName,
                paths,
            ).list()
        }
    }
}
