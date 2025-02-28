// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.data

import io.quarkus.hibernate.reactive.panache.kotlin.PanacheEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * A graphical image, such as an app icon or screenshot
 *
 * @property objectId the unique object ID of the corresponding image file in the configured object
 * storage bucket
 */
@Entity
@Table(name = "images")
class Image(
    @Column(columnDefinition = "text", name = "object_id", nullable = false)
    val objectId: String,
) : PanacheEntity()
