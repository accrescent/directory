// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.serde

import app.accrescent.events.v1.AppDownloadType
import app.accrescent.events.v1.appDownloaded
import app.accrescent.services.directory.data.events.AppDownloaded
import app.accrescent.services.directory.data.events.DownloadType
import org.apache.kafka.common.serialization.Serializer

/**
 * Kafka serializer for [AppDownloaded] events.
 */
class AppDownloadedSerializer : Serializer<AppDownloaded> {
    /**
     * @suppress
     */
    override fun serialize(topic: String, data: AppDownloaded): ByteArray {
        return appDownloaded {
            date = data.date.toString()
            appId = data.appId
            versionCode = data.versionCode.toInt()
            downloadType = when (data.downloadType) {
                DownloadType.INITIAL -> AppDownloadType.APP_DOWNLOAD_TYPE_INITIAL
                DownloadType.UPDATE -> AppDownloadType.APP_DOWNLOAD_TYPE_UPDATE
            }
            deviceSdkVersion = data.deviceSdkVersion.toInt()
            if (data.countryCode != null) {
                countryCode = data.countryCode
            }
        }.toByteArray()
    }
}
