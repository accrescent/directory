// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory.data

import io.quarkus.hibernate.reactive.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.reactive.panache.kotlin.PanacheEntityBase
import io.smallrye.mutiny.Uni
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.util.UUID

/**
 * Release information for a release channel for a specific app
 *
 * @property id this release channel's unique ID
 * @property appId the Android application ID of the corresponding app
 * @property name the canonical string representation of this release channel
 * @property versionName the user-visible name for the latest version published in this channel
 * @property versionCode the internal version code for the latest version published in this channel
 * @property buildApksResult the `BuildApksResult` object associated with this app version
 * @property apks the APKs associated with this app version
 */
@Entity
@Table(
    name = "release_channels",
    uniqueConstraints = [UniqueConstraint(columnNames = ["app_id", "name"])],
)
class ReleaseChannel(
    @Id
    val id: UUID,

    @Column(columnDefinition = "text", name = "app_id", nullable = false)
    val appId: String,

    @Column(columnDefinition = "text", nullable = false)
    val name: String,

    @Column(columnDefinition = "text", name = "version_name", nullable = false)
    val versionName: String,

    @Column(name = "version_code", nullable = false)
    val versionCode: UInt,

    @Column(columnDefinition = "bytea", name = "build_apks_result", nullable = false)
    val buildApksResult: ByteArray,

    @OneToMany(cascade = [CascadeType.ALL], mappedBy = "releaseChannel")
    @OnDelete(action = OnDeleteAction.CASCADE)
    val apks: Set<Apk>
) : PanacheEntityBase {
    @ManyToOne
    @JoinColumn(name = "app_id", insertable = false, updatable = false)
    private lateinit var app: App

    /**
     * Container for related methods
     */
    companion object : PanacheCompanion<ReleaseChannel> {
        /**
         * Finds a release channel by its app ID and canonical name
         *
         * @param appId the ID of the app to find a release channel for
         * @param name the canonical name of the release channel, such as "well_known_stable"
         * @return the release channel for the given app and name, or null if the app does not exist
         */
        fun findByAppIdAndName(appId: String, name: String): Uni<ReleaseChannel?> {
            return find("WHERE appId = ?1 AND name = ?2", appId, name).firstResult()
        }
    }
}
