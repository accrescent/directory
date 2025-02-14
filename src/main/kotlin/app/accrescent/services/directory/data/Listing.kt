// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.data

import io.quarkus.hibernate.reactive.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.reactive.panache.kotlin.PanacheEntity
import io.quarkus.runtime.annotations.RegisterForReflection
import io.smallrye.mutiny.Uni
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction

/**
 * An app listing to be displayed in the store
 *
 * @property appId the Android application ID of the corresponding app
 * @property language the BCP-47 language tag for the language this listing's content is written in
 * @property name the proper name of the app (optionally including some short descriptive text)
 * @property shortDescription a short summary of the app
 * @property icon the app's icon to be displayed in the store
 */
@Entity
@Table(
    name = "listings",
    uniqueConstraints = [UniqueConstraint(columnNames = ["app_id", "language"])],
)
class Listing(
    @Column(columnDefinition = "text", name = "app_id", nullable = false)
    val appId: String,

    @Column(columnDefinition = "text", nullable = false)
    val language: String,

    @Column(columnDefinition = "text", nullable = false)
    val name: String,

    @Column(columnDefinition = "text", name = "short_description", nullable = false)
    val shortDescription: String,

    @ManyToOne(cascade = [CascadeType.ALL])
    @JoinColumn(name = "icon_image_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val icon: Image,
) : PanacheEntity() {
    @ManyToOne
    @JoinColumn(name = "app_id", insertable = false, updatable = false)
    private lateinit var app: App

    /**
     * Container for related methods
     */
    companion object : PanacheCompanion<Listing> {
        /**
         * Finds all languages a given app has listings for
         *
         * @param appId the ID of the app to find listing languages for
         * @return the list of languages the provided app has listings for
         */
        fun findAllLanguagesForApp(appId: String): Uni<List<ListingLanguage>> {
            return find("appId", appId)
                .project(ListingLanguage::class.java)
                .list()
        }

        /**
         * Finds an app listing by its app ID and language
         *
         * @param appId the ID of the app to find a listing for
         * @param language the exact BCP-47 language code to find a listing for
         * @return the matching listing, or null if it does not exist
         */
        fun findByAppIdAndLanguage(appId: String, language: String): Uni<Listing?> {
            return find("appId = ?1 AND language = ?2", appId, language).firstResult()
        }

        /**
         * Finds the default listing for a given app
         *
         * @param appId the ID of the app to find a listing for
         * @return the default listing for the given app, or null if the app does not exist
         */
        fun findDefaultForApp(appId: String): Uni<Listing?> {
            return find(
                "FROM Listing listings " +
                "JOIN App apps ON apps.id = ?1 " +
                "WHERE listings.language = apps.defaultListingLanguage",
                appId,
            ).firstResult()
        }
    }
}

/**
 * A projection of [Listing.language]
 *
 * @property language the BCP-47 language code describing the listing's content
 */
@RegisterForReflection
data class ListingLanguage(val language: String)
