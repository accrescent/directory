// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.services.directory

import app.accrescent.directory.v1beta1.ReleaseChannel as DirectoryReleaseChannel
import build.buf.gen.accrescent.server.events.v1.ReleaseChannel as EventsReleaseChannel
import build.buf.gen.accrescent.server.events.v1.releaseChannel as eventsReleaseChannel

/**
 * The permanent, canonical name of the stable release channel
 */
const val RELEASE_CHANNEL_NAME_STABLE = "well_known_stable"

/**
 * Maps a release channel into its canonical string form
 */
fun DirectoryReleaseChannel.canonicalForm(): String = this.wellKnown.canonicalForm()

/**
 * Maps a release channel into its canonical string form
 */
fun EventsReleaseChannel.canonicalForm(): String = this.wellKnown.canonicalForm()

/**
 * Maps a well-known release channel into its canonical string form
 */
fun DirectoryReleaseChannel.WellKnown.canonicalForm(): String = when (this) {
    DirectoryReleaseChannel.WellKnown.UNRECOGNIZED,
    DirectoryReleaseChannel.WellKnown.WELL_KNOWN_UNSPECIFIED,
    DirectoryReleaseChannel.WellKnown.WELL_KNOWN_STABLE -> RELEASE_CHANNEL_NAME_STABLE
}

/**
 * Maps a well-known release channel into its canonical string form
 */
fun EventsReleaseChannel.WellKnown.canonicalForm(): String = when (this) {
    EventsReleaseChannel.WellKnown.UNRECOGNIZED,
    EventsReleaseChannel.WellKnown.WELL_KNOWN_UNSPECIFIED,
    EventsReleaseChannel.WellKnown.WELL_KNOWN_STABLE -> RELEASE_CHANNEL_NAME_STABLE
}

/**
 * Converts a canonical release channel name into a release channel.
 *
 * @return the release channel corresponding to this canonical name.
 * @throws IllegalArgumentException if this string is not a known canonical release channel name.
 */
fun String.toEventsReleaseChannel(): EventsReleaseChannel {
    val channel = when (this) {
        RELEASE_CHANNEL_NAME_STABLE -> EventsReleaseChannel.WellKnown.WELL_KNOWN_STABLE
        else -> throw IllegalArgumentException("")
    }

    return eventsReleaseChannel { wellKnown = channel }
}
