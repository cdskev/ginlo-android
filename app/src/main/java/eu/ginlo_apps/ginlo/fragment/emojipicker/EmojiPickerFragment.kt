// Copyright (c) 2020-2022 ginlo.net GmbH
package eu.ginlo_apps.ginlo.fragment.emojipicker

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import eu.ginlo_apps.ginlo.R
import eu.ginlo_apps.ginlo.model.Emoji
import kotlinx.android.synthetic.main.emoji_picker.emoji_backspace
import kotlinx.android.synthetic.main.emoji_picker.emoji_pager
import kotlinx.android.synthetic.main.emoji_picker.emoji_tabs
import java.io.BufferedInputStream

class EmojiPickerFragment: Fragment() {

    private val contentList = listOf(
            "emojis",
            "people",
            "animals",
            "plants",
            "foodAndDrinks",
            "tripsAndLocations",
            "activitiesAndEvents",
            "flags",
            "objects",
            "symbols"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.emoji_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapter(getSpanCount())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        setupAdapter(getSpanCount())
        super.onConfigurationChanged(newConfig)
    }

    private fun getSpanCount(): Int = when(resources.configuration.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> SYMBOL_COUNT_PORTRAIT
        Configuration.ORIENTATION_LANDSCAPE -> SYMBOL_COUNT_LANDSCAPE
        else -> SYMBOL_COUNT_PORTRAIT
    }

    private fun setupAdapter(spanCount: Int) {
        val pref = requireActivity()
            .application
            .getSharedPreferences(EMOJI_PREF, Context.MODE_PRIVATE)

        val recentlyUsed = LruCache<Int, String>(20).apply {
            pref.getStringSet(RECENTLY_USED, emptySet())?.forEachIndexed { index, it ->
                put(index, it)
            }
        }

        val activityCallback = requireActivity() as? EmojiPickerCallback

        val adapter =  EmojiPagerAdapter(
            contentList,
            readAllEmojis(),
            recentlyUsed,
            spanCount
        ) {
            val writer = pref.edit()
            writer.putStringSet(RECENTLY_USED, recentlyUsed.snapshot().values.toMutableSet())
            writer.apply()
            (activityCallback)?.onEmojiSelected(it)
        }

        emoji_pager.adapter = adapter
        emoji_tabs.setupWithViewPager(emoji_pager)

        emoji_backspace.setOnClickListener {
            (activityCallback)?.onBackSpaceSelected()
        }

        setupTabs()
    }

    private fun setupTabs() {
        emoji_tabs.getTabAt(0)?.setIcon(R.drawable.ic_recent)
        emoji_tabs.getTabAt(1)?.setIcon(R.drawable.ic_emoji)
        emoji_tabs.getTabAt(2)?.setIcon(R.drawable.ic_humans)
        emoji_tabs.getTabAt(3)?.setIcon(R.drawable.ic_animals)
        emoji_tabs.getTabAt(4)?.setIcon(R.drawable.ic_nature)
        emoji_tabs.getTabAt(5)?.setIcon(R.drawable.ic_lunch)
        emoji_tabs.getTabAt(6)?.setIcon(R.drawable.ic_traffic)
        emoji_tabs.getTabAt(7)?.setIcon(R.drawable.ic_events)
        emoji_tabs.getTabAt(8)?.setIcon(R.drawable.ic_flag)
        emoji_tabs.getTabAt(9)?.setIcon(R.drawable.ic_objects)
        emoji_tabs.getTabAt(10)?.setIcon(R.drawable.ic_symbols)
    }

    private fun readAllEmojis(): List<Emoji> =
        BufferedInputStream(requireContext().assets.open("emojis.json")).use { reader ->
            Gson().fromJson(String(reader.readBytes()), Array<Emoji>::class.java)
        }.toList()

    companion object {
        const val EMOJI_PREF = "emoji_picker"
        const val RECENTLY_USED = "recent"
        const val SYMBOL_COUNT_PORTRAIT = 8
        const val SYMBOL_COUNT_LANDSCAPE = 16
    }
}
