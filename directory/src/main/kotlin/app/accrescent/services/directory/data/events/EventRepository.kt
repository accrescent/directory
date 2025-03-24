// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.data.events

import io.agroal.api.AgroalDataSource
import io.quarkus.agroal.DataSource
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

/**
 * A repository class for managing server application events such as app downloads
 */
@ApplicationScoped
class EventRepository @Inject constructor(
    @DataSource("events")
    private val dataSource: AgroalDataSource,
) {
    /**
     * Adds a download event to the database
     *
     * @param download the download event to add
     */
    fun addDownload(download: Download) {
        dataSource.connection.use {
            it.prepareStatement(
                "INSERT INTO downloads (date, app_id, version_code, device_sdk_version, country_code) " +
                        "VALUES (?, ?, ?, ?, ?)"
            ).use {
                it.setObject(1, download.date)
                it.setString(2, download.appId)
                // Extend UInts to Longs so that large UInts don't get interpreted as negative
                it.setLong(3, download.versionCode.toLong())
                it.setLong(4, download.deviceSdkVersion.toLong())
                it.setString(5, download.countryCode ?: "")

                it.executeUpdate()
            }
        }
    }

    /**
     * Adds a listing view event to the database
     *
     * @param view the view event to add
     */
    fun addListingView(view: ListingView) {
        dataSource.connection.use {
            it.prepareStatement(
                "INSERT INTO listing_views (date, app_id, language_code, device_sdk_version, country_code) " +
                        "VALUES (?, ?, ?, ?, ?)"
            ).use {
                it.setObject(1, view.date)
                it.setString(2, view.appId)
                it.setString(3, view.languageCode)
                // Extend UInts to Longs so that large UInts don't get interpreted as negative
                it.setLong(4, view.deviceSdkVersion.toLong())
                it.setString(5, view.countryCode ?: "")

                it.executeUpdate()
            }
        }
    }
}
