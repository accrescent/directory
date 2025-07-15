// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory.data.events

import app.accrescent.events.v1.AppDownloadEvent
import app.accrescent.events.v1.AppDownloadType
import app.accrescent.events.v1.appDownloadEvent
import org.apache.kafka.common.serialization.Serializer

/**
 * Kafka serializer for app [Download] events.
 *
 * Serializes to protobuf instances of [AppDownloadEvent].
 */
class DownloadSerializer : Serializer<Download> {
    /**
     * @suppress
     */
    override fun serialize(topic: String, data: Download): ByteArray {
        return appDownloadEvent {
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
