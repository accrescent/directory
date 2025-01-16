// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.data

import io.quarkus.hibernate.reactive.panache.PanacheEntity
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

    @ManyToOne(cascade = [CascadeType.PERSIST])
    @JoinColumn(name = "icon_image_id", referencedColumnName = "id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val icon: Image,
) : PanacheEntity() {
    @ManyToOne
    @JoinColumn(name = "app_id", insertable = false, updatable = false)
    private lateinit var app: App
}
