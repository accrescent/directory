// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.data

import io.quarkus.hibernate.reactive.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.reactive.panache.kotlin.PanacheEntityBase
import io.quarkus.runtime.annotations.RegisterForReflection
import io.smallrye.mutiny.Uni
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

/**
 * A unique app listed in the store
 *
 * @property id a unique Android application ID
 * @property defaultListingLanguage the listing language to return from the API as a fallback
 * @property listings the app's store listings visible to clients
 * @property releaseChannels the app's current releases by channel
 */
@Entity
@Table(name = "apps")
class App(
    @Column(columnDefinition = "text")
    @Id
    val id: String,

    @Column(columnDefinition = "text", name = "default_listing_language", nullable = false)
    val defaultListingLanguage: String,

    @OneToMany(cascade = [CascadeType.ALL], mappedBy = "app")
    @OnDelete(action = OnDeleteAction.CASCADE)
    val listings: Set<Listing>,

    @OneToMany(cascade = [CascadeType.ALL], mappedBy = "app")
    @OnDelete(action = OnDeleteAction.CASCADE)
    val releaseChannels: Set<ReleaseChannel>,
) : PanacheEntityBase {
    /**
     * Container for related methods
     */
    companion object : PanacheCompanionBase<App, String> {
        /**
         * Finds default listing languages by the provided query parameters
         *
         * @param pageSize the number of items to fetch
         * @param skip the number of items to skip after the cursor before fetching items
         * @param afterAppId the cursor app ID to fetch items after. If null, items are fetched
         * starting with the first app ID.
         * @return a list of app default listing languages matching the provided query
         */
        fun findDefaultListingLanguagesByQuery(
            pageSize: UInt,
            skip: UInt,
            afterAppId: String?,
        ): Uni<List<AppDefaultListingLanguage>> {
            return if (afterAppId == null) {
                // Extend UInts to Longs so that Hibernate doesn't interpret large UInts as negative
                // Ints and cause errors
                find("ORDER BY id ASC LIMIT ?1 OFFSET ?2", pageSize.toLong(), skip.toLong())
            } else {
                find(
                    "WHERE id > ?1 ORDER BY id ASC LIMIT ?2 OFFSET ?3",
                    afterAppId,
                    pageSize.toLong(),
                    skip.toLong(),
                )
            }.project(AppDefaultListingLanguage::class.java).list()
        }
    }
}

/**
 * A projection of [App.id] and [App.defaultListingLanguage]
 *
 * @property id the app's Android application ID
 * @property defaultListingLanguage the default listing language to use as a fallback in the API
 */
@RegisterForReflection
data class AppDefaultListingLanguage(
    val id: String,
    val defaultListingLanguage: String,
)
