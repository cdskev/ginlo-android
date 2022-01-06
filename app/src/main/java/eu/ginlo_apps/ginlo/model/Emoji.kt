// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.model

data class Emoji(
        val category: String,
        val symbol: String,
        val attr: String = "",
        val hasColorVariations: Boolean = false,
        val type: EmojiType = EmojiType.NONE
)

enum class EmojiType {
    MALE, FEMALE, NONE
}
