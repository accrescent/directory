// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.data

import io.quarkus.hibernate.reactive.panache.kotlin.PanacheEntityBase
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
) : PanacheEntityBase
