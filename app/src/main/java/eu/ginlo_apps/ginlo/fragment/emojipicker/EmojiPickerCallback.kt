// Copyright (c) 2020-2022 ginlo.net GmbH

package eu.ginlo_apps.ginlo.fragment.emojipicker

interface EmojiPickerCallback {
    fun onEmojiSelected(unicode: String)
    fun onBackSpaceSelected()
}
