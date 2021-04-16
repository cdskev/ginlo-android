// Copyright (c) 2020-2021 ginlo.net GmbH
package eu.ginlo_apps.ginlo

internal fun String.sha256sum(): ByteArray =
    toByteArray().sha256Sum()
