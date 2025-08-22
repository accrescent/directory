// Copyright 2025 Logan Magee
//
// SPDX-License-Identifier: AGPL-3.0-only

package app.accrescent.server.directory

import build.buf.gen.accrescent.server.events.v1.App
import build.buf.gen.accrescent.server.events.v1.AppEditPublicationRequested
import build.buf.gen.accrescent.server.events.v1.AppPublicationRequested
import build.buf.gen.accrescent.server.events.v1.appPublicationRequested
import build.buf.gen.accrescent.server.events.v1.copy
import com.google.protobuf.TextFormat

object TestDataHelper {
    val validApp: App = TestDataHelper::class.java.classLoader
        .getResourceAsStream("valid-app.txtpb")!!
        .use {
            val builder = App.newBuilder()
            it.reader().use { TextFormat.merge(it, builder) }
            builder
        }
        .build()

    val validAppEditPublicationRequested: AppEditPublicationRequested = TestDataHelper::class.java
        .classLoader
        .getResourceAsStream("valid-app-edit-publication-requested.txtpb")!!
        .use {
            val builder = AppEditPublicationRequested.newBuilder()
            it.reader().use { TextFormat.merge(it, builder) }
            builder
        }
        .build()

    val validAppPublicationRequested: AppPublicationRequested = appPublicationRequested {
        app = validApp
    }
    val validAppPublicationRequested2: AppPublicationRequested = TestDataHelper::class.java
        .classLoader
        .getResourceAsStream("valid-app-publication-requested-2.txtpb")!!
        .use {
            val builder = AppPublicationRequested.newBuilder()
            it.reader().use { TextFormat.merge(it, builder) }
            builder
        }
        .build()
    val validAppPublicationRequested3Incompatible: AppPublicationRequested = TestDataHelper::class
        .java
        .classLoader
        .getResourceAsStream("valid-app-publication-requested-3-incompatible.txtpb")!!
        .use {
            val builder = AppPublicationRequested.newBuilder()
            it.reader().use { TextFormat.merge(it, builder) }
            builder
        }
        .build()

    val invalidAppEditPublicationRequested = validAppEditPublicationRequested.copy { clearEdit() }
    val invalidAppPublicationRequested = validAppPublicationRequested.copy { clearApp() }
}
