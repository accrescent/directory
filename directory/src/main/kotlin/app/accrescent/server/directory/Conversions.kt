// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory

import build.buf.gen.accrescent.server.events.v1.ReleaseChannel
import build.buf.gen.accrescent.server.events.v1.releaseChannel

/**
 * The permanent, canonical name of the stable release channel
 */
const val RELEASE_CHANNEL_NAME_STABLE = "well_known_stable"

/**
 * Maps a release channel into its canonical string form
 */
fun ReleaseChannel.canonicalForm(): String = this.wellKnown.canonicalForm()

/**
 * Maps a well-known release channel into its canonical string form
 */
fun ReleaseChannel.WellKnown.canonicalForm(): String = when (this) {
    ReleaseChannel.WellKnown.UNRECOGNIZED,
    ReleaseChannel.WellKnown.WELL_KNOWN_UNSPECIFIED,
    ReleaseChannel.WellKnown.WELL_KNOWN_STABLE -> RELEASE_CHANNEL_NAME_STABLE
}

/**
 * Converts a canonical release channel name into a release channel.
 *
 * @return the release channel corresponding to this canonical name.
 * @throws IllegalArgumentException if this string is not a known canonical release channel name.
 */
fun String.toReleaseChannel(): ReleaseChannel {
    val channel = when (this) {
        RELEASE_CHANNEL_NAME_STABLE -> ReleaseChannel.WellKnown.WELL_KNOWN_STABLE
        else -> throw IllegalArgumentException("")
    }

    return releaseChannel { wellKnown = channel }
}
